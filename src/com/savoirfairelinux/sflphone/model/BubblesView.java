package com.savoirfairelinux.sflphone.model;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;

public class BubblesView extends SurfaceView implements SurfaceHolder.Callback, OnTouchListener
{
	private static final String TAG = BubblesView.class.getSimpleName();

	private BubblesThread thread = null;
	private BubbleModel model;

	public BubblesView(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		SurfaceHolder holder = getHolder();
		holder.addCallback(this);

		// create thread only; it's started in surfaceCreated()
		createThread();

		setOnTouchListener(this);
		setFocusable(true);
	}

	private void createThread()
	{
		if (thread != null)
			return;
		thread = new BubblesThread(getHolder(), getContext(), new Handler() {
			@Override
			public void handleMessage(Message m)
			{
				/*  mStatusText.setVisibility(m.getData().getInt("viz"));
				  mStatusText.setText(m.getData().getString("text"));*/
			}
		});
		if (model != null)
			thread.setModel(model);
	}

	public void setModel(BubbleModel model)
	{
		this.model = model;
		thread.setModel(model);
	}

	/*@Override
	public void onWindowFocusChanged(boolean hasWindowFocus) {
		if (!hasWindowFocus) {
			thread.pause();
		}
	}*/

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
	{
		Log.w(TAG, "surfaceChanged");
		thread.setSurfaceSize(width, height);
	}

	/*
	 * Callback invoked when the Surface has been created and is ready to be
	 * used.
	 */
	@Override
	public void surfaceCreated(SurfaceHolder holder)
	{
		// start the thread here so that we don't busy-wait in run()
		// waiting for the surface to be created
		createThread();

		Log.w(TAG, "surfaceCreated");
		thread.setRunning(true);
		thread.start();
	}

	/*
	 * Callback invoked when the Surface has been destroyed and must no longer
	 * be touched. WARNING: after this method returns, the Surface/Canvas must
	 * never be touched again!
	 */
	@Override
	public void surfaceDestroyed(SurfaceHolder holder)
	{
		// we have to tell thread to shut down & wait for it to finish, or else
		// it might touch the Surface after we return and explode
		Log.w(TAG, "surfaceDestroyed");
		boolean retry = true;
		thread.setRunning(false);
		while (retry) {
			try {
				thread.join();
				retry = false;
			} catch (InterruptedException e) {
			}
		}
		thread = null;
	}

	@Override
	public boolean onTouch(View v, MotionEvent event)
	{
		Log.w(TAG, "onTouch " + event.getAction());

		int action = event.getActionMasked();

		if (action == MotionEvent.ACTION_DOWN) {
			for (Bubble b : model.listBubbles) {
				if (b.intersects(event.getX(), event.getY())) {
					b.dragged = true;
					b.last_drag = System.nanoTime();
				}
			}
		} else if (action == MotionEvent.ACTION_MOVE) {
			for (Bubble b : model.listBubbles) {
				if (b.dragged) {
					float x = event.getX(), y = event.getY();
					long now = System.nanoTime();
					float dt = (float) ((now-b.last_drag)/1000000000.);
					float dx = x - b.getPosX(), dy = y - b.getPosY();
					b.last_drag = now;

					b.setPos(event.getX(), event.getY());
					/*int hn = event.getHistorySize() - 2;
					Log.w(TAG, "event.getHistorySize() : " + event.getHistorySize());
					if(hn > 0) {
						float dx = x-event.getHistoricalX(hn);
						float dy = y-event.getHistoricalY(hn);
						float dt = event.getHistoricalEventTime(hn)/1000.f;*/
					b.speed.x = dx/dt;
					b.speed.y = dy/dt;
					//Log.w(TAG, "onTouch dx:" + b.speed.x + " dy:" + b.speed.y);
					//}
					return true;
				}
			}
		} else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
			for (Bubble b : model.listBubbles) {
				if (b.dragged) {
					b.dragged = false;
				}
			}
		}
		return true;
	}

	class BubblesThread extends Thread
	{
		private boolean running = false;
		private SurfaceHolder surfaceHolder;

		BubbleModel model = null;

		public BubblesThread(SurfaceHolder holder, Context context, Handler handler)
		{
			surfaceHolder = holder;
		}

		public void setModel(BubbleModel model)
		{
			this.model = model;
		}

		@Override
		public void run()
		{
			while (running) {
				Canvas c = null;
				try {
					c = surfaceHolder.lockCanvas(null);

					// for the case the surface is destroyed while already in the loop
					if (c == null || model == null)
						continue;

					synchronized (surfaceHolder) {
						//Log.w(TAG, "Thread doDraw");
						model.update();
						doDraw(c);
					}
				} finally {
					// do this in a finally so that if an exception is thrown
					// during the above, we don't leave the Surface in an
					// inconsistent state
					if (c != null)
						surfaceHolder.unlockCanvasAndPost(c);
				}
			}
		}

		public void setRunning(boolean b)
		{
			running = b;
		}

		public void setSurfaceSize(int width, int height)
		{
			synchronized (surfaceHolder) {
				if (model != null) {
					model.width = width;
					model.height = height;
				}

				// don't forget to resize the background image
				//  mBackgroundImage = Bitmap.createScaledBitmap(mBackgroundImage, width, height, true);
			}
		}

		private void doDraw(Canvas canvas)
		{
			canvas.drawColor(Color.WHITE);

			synchronized (model) {
				for (int i = 0; i < model.listBubbles.size(); i++) {
					Bubble b = model.listBubbles.get(i);
					canvas.drawBitmap(b.getBitmap(), null, b.getBounds(), null);
				}
			}
		}
	}

}
