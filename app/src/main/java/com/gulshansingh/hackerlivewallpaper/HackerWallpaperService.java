package com.gulshansingh.hackerlivewallpaper;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Build;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.service.wallpaper.WallpaperService;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.util.ArrayList;
import java.util.List;

import static com.gulshansingh.hackerlivewallpaper.SettingsActivity.KEY_BACKGROUND_COLOR;

public class HackerWallpaperService extends WallpaperService {

	private static boolean reset = false;
	private static boolean previewReset = false;

	public static void reset() {
		previewReset = true;
		reset = true;
	}

	@Override
	public Engine onCreateEngine() {
		return new HackerWallpaperEngine();
	}

	public class HackerWallpaperEngine extends Engine implements
			Choreographer.FrameCallback {

		/**
		 * Cap the animation at ~60fps. High refresh rate displays (e.g. the
		 * 120Hz Pixel 10 Pro XL) would otherwise draw twice as many frames
		 * for no visual benefit, wasting battery.
		 */
		private static final long TARGET_FRAME_TIME_MS = 1000 / 60;

		private final Choreographer choreographer = Choreographer.getInstance();

		private boolean visible = false;
		private boolean running = false;
		private long lastDrawTime = 0;

		/** The sequences to draw on the screen */
		private final List<BitSequence> sequences = new ArrayList<>();

		private int width;
		private int r, g, b;

		/**
		 * Called by the Choreographer on every display vsync while the
		 * wallpaper is visible. All animation state is updated and drawn here,
		 * on the main thread, so no synchronization is needed.
		 */
		@Override
		public void doFrame(long frameTimeNanos) {
			if (!running) {
				return;
			}

			long now = frameTimeNanos / 1_000_000L;
			if (now - lastDrawTime >= TARGET_FRAME_TIME_MS) {
				lastDrawTime = now;
				drawFrame(now);
			}

			choreographer.postFrameCallback(this);
		}

		/** Updates and draws all of the bit sequences */
		private void drawFrame(long now) {
			// We can't have just one reset flag, because then the preview
			// would consume that flag and the actual wallpaper wouldn't be
			// reset
			if (previewReset && isPreview()) {
				previewReset = false;
				resetSequences(now);
			} else if (reset && !isPreview()) {
				reset = false;
				resetSequences(now);
			}

			SurfaceHolder holder = getSurfaceHolder();
			Canvas c = null;
			try {
				// Hardware canvases render on the GPU, which is much faster
				// and more power efficient than software rendering,
				// especially at high resolutions. BlurMaskFilter is only
				// hardware accelerated on API 28+.
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
					c = holder.lockHardwareCanvas();
				} else {
					c = holder.lockCanvas();
				}
				if (c != null) {
					c.drawARGB(255, r, g, b);

					for (int i = 0; i < sequences.size(); i++) {
						BitSequence sequence = sequences.get(i);
						sequence.update(now);
						sequence.draw(c);
					}
				}
			} finally {
				if (c != null) {
					holder.unlockCanvasAndPost(c);
				}
			}
		}

		private void resetSequences(long now) {
			SharedPreferences preferences = PreferenceManager
					.getDefaultSharedPreferences(getApplicationContext());
			int color = preferences.getInt(KEY_BACKGROUND_COLOR, 0);
			r = (color >> 16) & 0xFF;
			g = (color >> 8) & 0xFF;
			b = color & 0xFF;
			sequences.clear();
			int numSequences = (int) (1.5 * width / BitSequence.getWidth());
			for (int i = 0; i < numSequences; i++) {
				sequences.add(new BitSequence(
						(int) (i * BitSequence.getWidth() / 1.5), now));
			}
		}

		private void start() {
			if (!running) {
				running = true;
				lastDrawTime = 0;
				long now = SystemClock.uptimeMillis();
				for (int i = 0; i < sequences.size(); i++) {
					sequences.get(i).resume(now);
				}
				choreographer.postFrameCallback(this);
			}
		}

		private void stop() {
			if (running) {
				running = false;
				choreographer.removeFrameCallback(this);
			}
		}

		@Override
		public void onSurfaceCreated(SurfaceHolder holder) {
			super.onSurfaceCreated(holder);
			BitSequence.configure(getApplicationContext());
		}

		@Override
		public void onSurfaceDestroyed(SurfaceHolder holder) {
			super.onSurfaceDestroyed(holder);
			stop();
		}

		@Override
		public void onSurfaceChanged(SurfaceHolder holder, int format,
				int width, int height) {
			super.onSurfaceChanged(holder, format, width, height);
			this.width = width;

			BitSequence.setScreenDim(width, height);

			// Tell the compositor we only need ~60Hz, letting LTPO displays
			// drop out of 120Hz mode while the wallpaper is visible
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				holder.getSurface().setFrameRate(60f,
						Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
			}

			resetSequences(SystemClock.uptimeMillis());

			if (visible) {
				start();
			}
		}

		@Override
		public void onVisibilityChanged(boolean visible) {
			super.onVisibilityChanged(visible);
			this.visible = visible;
			if (visible) {
				start();
			} else {
				stop();
			}
		}

		@Override
		public void onDestroy() {
			super.onDestroy();
			stop();
		}
	}
}
