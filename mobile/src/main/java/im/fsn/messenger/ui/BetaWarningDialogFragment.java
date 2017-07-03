package im.fsn.messenger.ui;

import im.fsn.messenger.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

public class BetaWarningDialogFragment extends DialogFragment {

	static BetaWarningDialogFragment newInstance() {
		BetaWarningDialogFragment d = new BetaWarningDialogFragment();
		return d;
	}

	public BetaWarningDialogFragment() {

	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.app_name);
		builder.setMessage(R.string.BetaWarning);
		builder.setNeutralButton(android.R.string.ok, null);
		return builder.create();

	}

	@Override
	public void onDestroyView() {
		if (getDialog() != null && getRetainInstance())
			getDialog().setOnDismissListener(null);
		super.onDestroyView();
	}
}
