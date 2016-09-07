package sword.blemesh.meshmessager.ui.fragment;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import sword.blemesh.meshmessager.R;
import sword.blemesh.meshmessager.database.DbManager;
import sword.blemesh.meshmessager.ui.adapter.UserListAdapter;

/**
 * Created by 力 on 2016/9/2.
 * Fragment used for displaying all the nearby users's infomations;
 * choose one of them can launch a chat with him
 */
public class UserListFragment extends Fragment implements UserListAdapter.MessageSelectedListener{

    public interface ChatFragmentCallback {
        public void onMessageSelected(View identiconView, View usernameView, String peerAddress);
    }

    private ChatFragmentCallback mCallback;
    private DbManager dbManager;
    RecyclerView mRecyclerView;
    UserListAdapter mAdapter;
    View mRoot;

    public UserListFragment(){

    }

    public void setDbManager(DbManager dbManager){
        this.dbManager = dbManager;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        if (dbManager == null)
            throw new IllegalStateException("MessageListFragment must be equipped with a DataStore. Did you call #setDataStore");

    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        mRoot = inflater.inflate(R.layout.fragment_userlist, container, false);
        mRecyclerView = (RecyclerView) mRoot.findViewById(R.id.userList_recyclerView);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAdapter = new UserListAdapter(getActivity(), dbManager, this);
        mRecyclerView.setAdapter(mAdapter);
        return mRoot;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    //<editor-fold desc="UserListAdapter.MessageSelectedListener">
    @Override
    public void onMessageSelected(View identiconView, View usernameView, String peerAddress) {

    }
    //</editor-fold>


    public void animateIn() {
        mRoot.setAlpha(0);
        ObjectAnimator animator = ObjectAnimator.ofFloat(mRoot, "alpha", 0f, 1f)
                .setDuration(300);

        animator.setStartDelay(550);
        animator.start();
    }
}
