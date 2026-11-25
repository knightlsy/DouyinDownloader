package com.zz.douyin.hook;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 图集下载前的选图对话框（跑在抖音进程里）。
 *
 * ## 为什么要有它
 * 原来点「图片（无水印 · 3 张）」就是全下。图文作品动辄十几张，
 * 用户常常只想要其中两三张，全下之后还得去相册手动删一堆。
 *
 * ## 为什么不用 Compose
 * 这段代码注入在抖音进程里，只带得动 Android framework 的类。
 * Compose 那一整套依赖打进来体积和类冲突都不可接受，
 * 所以界面全部用 View 代码搭，图片也自己下载 —— 不能引 Coil。
 *
 * ## 主题
 * 不写死颜色，尽量跟随宿主（抖音）当前的浅色/深色。
 * 仅在拿不到主题色时才退到第 3 步的兜底值。
 */
final class ImagePickerDialog {

    private static final String TAG = "DouyinImmersive";
    /** 缩略图下载线程。3 个够用，再多抢宿主自己的网络。 */
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(48);

    private ImagePickerDialog() {
    }

    interface Callback {
        /**
         * @param indices 选中图片在原列表中的下标，升序
         */
        void onConfirm(List<Integer> indices);
    }

    static void show(
            Activity activity,
            List<FeedContentTracker.ImageUrl> images,
            Callback callback
    ) {
        if (activity == null || activity.isFinishing() || images == null || images.isEmpty()) {
            return;
        }

        final int dp = Math.max(1, (int) activity.getResources().getDisplayMetrics().density);
        final int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        // 对话框左右各 16dp，网格 3 列、列间距 3dp
        final int cellSize = (screenWidth - dp * 32 - dp * 6) / 3;

        final Set<Integer> selected = new HashSet<>();
        for (int index = 0; index < images.size(); index++) {
            selected.add(index);
        }

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp * 16, dp * 14, dp * 16, dp * 10);

        TextView title = new TextView(activity);
        title.setText("选择要下载的图片");
        title.setTextSize(17f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        final TextView summary = new TextView(activity);
        summary.setTextSize(12f);
        summary.setPadding(0, dp * 3, 0, dp * 8);
        root.addView(summary);

        GridView grid = new GridView(activity);
        grid.setNumColumns(3);
        grid.setHorizontalSpacing(dp * 3);
        grid.setVerticalSpacing(dp * 3);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        // GridView 在 Dialog 里没有父约束时高度会是 0，必须自己算：
        // 每行 cellSize，最多露 4 行，再多就滚动
        int rows = (images.size() + 2) / 3;
        int visibleRows = Math.min(rows, 4);
        grid.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                visibleRows * cellSize + Math.max(0, visibleRows - 1) * dp * 3
        ));

        final ImageAdapter adapter = new ImageAdapter(
                activity, images, selected, cellSize, dp, summary,
                () -> grid.invalidateViews()
        );
        grid.setAdapter(adapter);
        root.addView(grid);

        // ── 底部：全选 / 取消 / 下载 ──
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp * 10, 0, 0);

        TextView toggleAll = new TextView(activity);
        toggleAll.setTextSize(13f);
        toggleAll.setPadding(dp * 4, dp * 8, dp * 4, dp * 8);
        actions.addView(toggleAll);

        View spacer = new View(activity);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        actions.addView(spacer);

        TextView cancel = new TextView(activity);
        cancel.setText("取消");
        cancel.setTextSize(14f);
        cancel.setPadding(dp * 14, dp * 10, dp * 14, dp * 10);
        actions.addView(cancel);

        TextView confirm = new TextView(activity);
        confirm.setTextSize(14f);
        confirm.setTypeface(null, android.graphics.Typeface.BOLD);
        confirm.setPadding(dp * 18, dp * 10, dp * 18, dp * 10);
        actions.addView(confirm);

        root.addView(actions);

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // 圆角背板：不依赖宿主的卡片背景，自己画一层
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(dp * 16);
            background.setColor(resolveSurfaceColor(activity));
            root.setBackground(background);
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        // 选中数变化时刷新文案与按钮态
        Runnable refresh = () -> {
            int count = selected.size();
            summary.setText(String.format(Locale.US, "已选 %d / %d 张", count, images.size()));
            summary.setTextColor(count == 0 ? 0xFF999999 : 0xFFFF6B35);
            toggleAll.setText(count == images.size() ? "取消全选" : "全选");
            confirm.setText(count == 0 ? "未选择" : "下载 " + count + " 张");
            confirm.setAlpha(count == 0 ? 0.4f : 1f);
            adapter.notifyDataSetChanged();
        };
        refresh.run();

        toggleAll.setOnClickListener(v -> {
            if (selected.size() == images.size()) {
                selected.clear();
            } else {
                for (int index = 0; index < images.size(); index++) {
                    selected.add(index);
                }
            }
            refresh.run();
        });

        cancel.setOnClickListener(v -> dialog.dismiss());

        confirm.setOnClickListener(v -> {
            if (selected.isEmpty()) {
                return;
            }
            List<Integer> picked = new ArrayList<>(selected);
            Collections.sort(picked);
            dialog.dismiss();
            callback.onConfirm(picked);
        });

        dialog.setOnDismissListener(d -> {
            // 关掉时清掉这一批缩略图，别把宿主的堆占着
            CACHE.evictAll();
        });
        dialog.show();
    }

    /**
     * 网格单元：缩略图 + 序号 + 勾选。
     *
     * 图片是异步下载的，getView 里先把占位放上去，加载完再 setImageBitmap。
     * 复用 convertView 时用 tag 绑定 url，防止「划走再划回来显示成别的图」。
     */
    private static final class ImageAdapter extends BaseAdapter {

        private final Activity activity;
        private final List<FeedContentTracker.ImageUrl> images;
        private final Set<Integer> selected;
        private final int cellSize;
        private final int dp;
        private final TextView summary;
        private final Runnable onSelectionChanged;

        ImageAdapter(
                Activity activity,
                List<FeedContentTracker.ImageUrl> images,
                Set<Integer> selected,
                int cellSize,
                int dp,
                TextView summary,
                Runnable onSelectionChanged
        ) {
            this.activity = activity;
            this.images = images;
            this.selected = selected;
            this.cellSize = cellSize;
            this.dp = dp;
            this.summary = summary;
            this.onSelectionChanged = onSelectionChanged;
        }

        @Override
        public int getCount() {
            return images.size();
        }

        @Override
        public Object getItem(int position) {
            return images.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            FrameLayout cell;
            if (convertView instanceof FrameLayout) {
                cell = (FrameLayout) convertView;
            } else {
                cell = new FrameLayout(activity);
                cell.setLayoutParams(new GridView.LayoutParams(cellSize, cellSize));

                ImageView thumb = new ImageView(activity);
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumb.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
                thumb.setBackgroundColor(0xFFEEEEEE);
                cell.addView(thumb);

                TextView badge = new TextView(activity);
                badge.setTextSize(9f);
                badge.setTextColor(Color.WHITE);
                badge.setPadding(dp * 3, dp, dp * 3, dp);
                badge.setBackgroundColor(0x99000000);
                FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                badgeParams.gravity = Gravity.START | Gravity.TOP;
                badgeParams.setMargins(dp * 3, dp * 3, 0, 0);
                cell.addView(badge, badgeParams);

                CheckBox check = new CheckBox(activity);
                check.setClickable(false);
                check.setFocusable(false);
                FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(
                        dp * 26, dp * 26
                );
                checkParams.gravity = Gravity.END | Gravity.TOP;
                checkParams.setMargins(0, dp * 2, dp * 2, 0);
                cell.addView(check, checkParams);

                TextView live = new TextView(activity);
                live.setTextSize(8f);
                live.setTextColor(Color.WHITE);
                live.setPadding(dp * 3, dp, dp * 3, dp);
                live.setBackgroundColor(0x99000000);
                FrameLayout.LayoutParams liveParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                liveParams.gravity = Gravity.START | Gravity.BOTTOM;
                liveParams.setMargins(dp * 3, 0, 0, dp * 3);
                cell.addView(live, liveParams);

                cell.setTag(new CellHolder(thumb, badge, check, live));
            }

            CellHolder holder = (CellHolder) cell.getTag();
            FeedContentTracker.ImageUrl image = images.get(position);
            String url = image.primary();

            holder.badge.setText(String.valueOf(position + 1));
            holder.check.setChecked(selected.contains(position));
            holder.live.setVisibility(image.isLive() ? View.VISIBLE : View.GONE);
            if (image.isLive()) {
                holder.live.setText("实况");
            }

            holder.thumb.setTag(url);
            Bitmap cached = CACHE.get(url);
            if (cached != null) {
                holder.thumb.setImageBitmap(cached);
            } else {
                holder.thumb.setImageDrawable(null);
                holder.thumb.setBackgroundColor(0xFFEEEEEE);
                loadThumb(url, holder.thumb);
            }

            cell.setOnClickListener(v -> {
                if (!selected.remove(position)) {
                    selected.add(position);
                }
                onSelectionChanged.run();
            });
            return cell;
        }

        /**
         * 异步取缩略图。
         *
         * 用 SDK 自带的下采样解码，不把原图整个读进内存 ——
         * 图集里一张 2160×2880 的图解码后是 20MB 级别，
         * 9 张同时加载足够把抖音进程的堆打爆。
         */
        private void loadThumb(String url, ImageView target) {
            POOL.execute(() -> {
                Bitmap bitmap = decode(url);
                if (bitmap == null) {
                    return;
                }
                CACHE.put(url, bitmap);
                activity.runOnUiThread(() -> {
                    // 只在这个 ImageView 还挂着同一个 url 时才贴图，
                    // 否则复用时会把 A 图的缩略图贴到 B 格上
                    if (url.equals(target.getTag())) {
                        target.setImageBitmap(bitmap);
                    }
                });
            });
        }

        private Bitmap decode(String url) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("User-Agent", PLAYER_LIKE_UA);
                connection.setRequestProperty("Referer", "https://www.douyin.com/");

                // 第一遍只读边界，算降采样倍数
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream probe = connection.getInputStream()) {
                    BitmapFactory.decodeStream(probe, null, bounds);
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    return null;
                }
                int sample = Math.max(1, Math.min(bounds.outWidth, bounds.outHeight) / (cellSize * 2));

                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("User-Agent", PLAYER_LIKE_UA);
                connection.setRequestProperty("Referer", "https://www.douyin.com/");
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = Math.max(1, sample);
                try (InputStream input = connection.getInputStream()) {
                    return BitmapFactory.decodeStream(input, null, options);
                }
            } catch (Throwable error) {
                Log.d(TAG, "缩略图加载失败 " + error.getClass().getSimpleName());
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }

    private static final class CellHolder {
        final ImageView thumb;
        final TextView badge;
        final CheckBox check;
        final TextView live;

        CellHolder(ImageView thumb, TextView badge, CheckBox check, TextView live) {
            this.thumb = thumb;
            this.badge = badge;
            this.check = check;
            this.live = live;
        }
    }

    /** 跟随宿主深浅色的背板颜色。取不到就用中性灰白。 */
    private static int resolveSurfaceColor(Activity activity) {
        try {
            android.util.TypedValue value = new android.util.TypedValue();
            boolean resolved = activity.getTheme().resolveAttribute(
                    android.R.attr.colorBackground, value, true);
            if (resolved && value.data != 0) {
                // 有些主题给的是引用，保证不透明
                return value.data | 0xFF000000;
            }
        } catch (Throwable ignored) {
            // 落到兜底
        }
        return 0xFFF7F7F7;
    }

    /** 和播放链路一致的 UA，抖音 CDN 对陌生 UA 会限速 */
    private static final String PLAYER_LIKE_UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Mobile Safari/537.36";
}
