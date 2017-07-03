package im.fsn.messenger.ui;

import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;

public class SlideDownAnimation extends Animation {

	int mFromHeight;
	View mView;

	public SlideDownAnimation(View view) {
		this.mView = view;
		this.mFromHeight = view.getMeasuredHeight();
	}

	@Override
	protected void applyTransformation(float interpolatedTime, Transformation t) {
		if (mFromHeight == 0)
			this.mFromHeight = mView.getMeasuredHeight();
		int newHeight = (int) (mFromHeight * interpolatedTime);
		mView.getLayoutParams().height = newHeight;
		mView.requestLayout();

	}

	@Override
	public void initialize(int width, int height, int parentWidth,
			int parentHeight) {
		super.initialize(width, height, parentWidth, parentHeight);
	}

	@Override
	public boolean willChangeBounds() {
		return true;
	}
}