package sword.blemesh.singlehop.ui.fragment;


import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import sword.blemesh.singlehop.R;

/**
 * A simple {@link Fragment} subclass.
 */
public class QuoteWritingFragment extends Fragment {

    public static interface WritingFragmentListener {

        public void onShareRequested(String quote);
        public void onBothSendAndReceive(String quote);
        public void onReceiveButtonClick();
    }

    private WritingFragmentListener listener;
    private EditText quoteEntry;

    public QuoteWritingFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_writing, container, false);
        quoteEntry = (EditText) root.findViewById(R.id.quote_entry);

        root.findViewById(R.id.share_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showModeDialog();
            }
        });

        return root;
    }

    public void showModeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        String items[] = {getString(R.string.mode_both),
                getString(R.string.mode_scan),
                getString(R.string.mode_advertise)};
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        listener.onBothSendAndReceive(quoteEntry.getText().toString());
                        break;
                    case 1:
                        listener.onShareRequested(quoteEntry.getText().toString());
                        break;
                    case 2:
                        listener.onReceiveButtonClick();
                        break;
                    default:
                        throw new IllegalStateException("Press illegal button in dialog");
                }
            }
        });
        AlertDialog dialog=builder.create();//获取dialog
        dialog.show();//显示对话框
    }



    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (WritingFragmentListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement WritingFragmentListener");
        }
    }


}
