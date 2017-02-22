package sword.blemesh.meshmessager;

import android.animation.ValueAnimator;
import android.content.DialogInterface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.transition.Slide;
import android.transition.TransitionSet;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import im.delight.android.identicons.Identicon;
import sword.blemesh.meshmessager.database.DbManager;
import sword.blemesh.meshmessager.ui.fragment.MessageFragment;
import sword.blemesh.meshmessager.ui.fragment.UserListFragment;
import sword.blemesh.meshmessager.ui.fragment.WelcomeFragment;
import sword.blemesh.sdk.app.BleMeshService;
import sword.blemesh.sdk.app.ui.BleMeshFragment;
import sword.blemesh.sdk.mesh_graph.Peer;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        WelcomeFragment.WelcomeFragmentListener,
        BleMeshFragment.Callback,
        ChatManager.ChatManagerCallback,
        UserListFragment.UserListFragmentCallback,
        MessageFragment.MessageFragmentCallback
{
    public static String local_mac_address;

    private boolean BLEisRunning = false;

    private DrawerLayout drawer;
    private Toolbar toolbar;
    private TextView logView;

    private ChatManager mChatManager;
    private BleMeshFragment bleMeshFragment;
    private UserListFragment userListFragment;

    private TextView profileNameTv;
    private TextView profileInterestTv;
    private Identicon profileIdenticon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState!=null) {
            BLEisRunning = savedInstanceState.getBoolean("BLEisRunning");

        }
        setContentView(R.layout.activity_main);
        local_mac_address = android.provider.Settings.Secure.getString(getApplicationContext().getContentResolver(), "bluetooth_address");

        //initialization
        mChatManager = new ChatManager(getApplicationContext(),this);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        assert drawer != null;
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        assert navigationView != null;
        navigationView.setNavigationItemSelectedListener(this);
        View header_view = navigationView.getHeaderView(0);
        profileIdenticon = (Identicon) header_view.findViewById(R.id.identicon_profile);
        profileNameTv = (TextView) header_view.findViewById(R.id.username_profile);
        profileInterestTv = (TextView) header_view.findViewById(R.id.userinfo_profile);

        logView = (TextView)findViewById(R.id.ble_mesh_log);
        //Check and register user alias name
        if(savedInstanceState==null){
            Timber.d("First Launch App, run checkUserRegistered()");
            checkUserRegistered();
        }

        getSupportFragmentManager().addOnBackStackChangedListener(new android.support.v4.app.FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                int numEntries = getSupportFragmentManager().getBackStackEntryCount();
                if (numEntries == 0) {
                    userListFragment.animateIn();
//                    tintSystemBars(mPalette.getVibrantColor(R.color.primary), mPalette.getDarkVibrantColor(R.color.primaryDark),
//                            getResources().getColor(R.color.primary), getResources().getColor(R.color.primaryDark));

                    setTitle(getString(R.string.app_name));
                }
            }
        });
    }

    private void checkUserRegistered() {
        Peer localPeer = mChatManager.getLocalPeer();
        if(localPeer != null){
            //TODO：若用户已注册后的处理
            // Register ourselves with the AirShare Service, using our own user model's alias
            if (bleMeshFragment == null) {
                bleMeshFragment = BleMeshFragment.newInstance(localPeer.getAlias(), ChatManager.BLE_MESH_SERVICE_NAME, this);
                bleMeshFragment.setShouldServiceContinueInBackground(true);
                getSupportFragmentManager().beginTransaction()
                        .add(bleMeshFragment, "bleMesh")
                        .commit();
                Timber.d("Add bleMesh fragment");
            }

            //set profile drawer
            profileNameTv.setText(localPeer.getAlias());
            profileIdenticon.show(localPeer.getAlias());


            //display user list fragment
            userListFragment = new UserListFragment();
            userListFragment.setDbManager(mChatManager.getDbManager());

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, userListFragment, "userList")
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .commit();

        }else{
            toolbar.setVisibility(View.GONE);
            getWindow().setStatusBarColor(getResources().getColor(R.color.welcome_status_bar));
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, new WelcomeFragment())
                    .commit();
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        assert drawer != null;
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }


    //<editor-fold desc="WelcomeFragment.WelcomeFragmentListener">
    @Override
    public void onUsernameSelected(String username,String userinfo) {
        toolbar.setVisibility(View.VISIBLE);
        getWindow().setStatusBarColor(getResources().getColor(R.color.primaryDark));
        //insert to db
        mChatManager.createLocalPeer(username,local_mac_address);

        //check again
        checkUserRegistered();
    }
    //</editor-fold>

    //<editor-fold desc="BleMeshFragment.Callback">
    @Override
    public void onServiceReady(@NonNull BleMeshService.ServiceBinder serviceBinder) {
        mChatManager.setServiceBinder(serviceBinder);
        //mChatManager.setCallback()

    }

    @Override
    public void onFinished(@Nullable Exception exception) {
    }
    //</editor-fold>

    //<editor-fold desc="ChatManager Callback"

    @Override
    public void onNewLog(@NonNull String logText) {
        logView.append(logText + "\n");
    }


    //</editor-fold>

    //<editor-fold desc="Toolbar and Drawer Button">
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        if (!BLEisRunning) {
            menu.getItem(0).setTitle(R.string.service_switch_start);
        } else {
            menu.getItem(0).setTitle(R.string.service_switch_stop);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch(id){
            case R.id.action_switch:

                if (!BLEisRunning) {
                    item.setTitle(R.string.service_switch_stop);
                    showModeDialog();
                    BLEisRunning = true;
                } else {
                    mChatManager.stopBleMesh();
                    item.setTitle(R.string.service_switch_start);
                    //TODO: test , to be deleted
                    mChatManager.resetData();
                    BLEisRunning = false;
                }
            break;

            case R.id.action_view_logs:
                if (drawer.isDrawerOpen(GravityCompat.END)) {
                    drawer.closeDrawer(GravityCompat.END);
                } else {
                    drawer.openDrawer(GravityCompat.END);
                }
            break;

        }
        return super.onOptionsItemSelected(item);
    }

    public void showModeDialog(){
        AlertDialog.Builder builder= new AlertDialog.Builder(this);
        String items[] = {getString(R.string.mode_both),
                getString(R.string.mode_scan),
                getString(R.string.mode_advertise)};
        builder.setItems(items,new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        mChatManager.startBleMesh(ChatManager.BleMode.mode_both);
                        break;
                    case 1:
                        mChatManager.startBleMesh(ChatManager.BleMode.mode_scan);
                        break;
                    case 2:
                        mChatManager.startBleMesh(ChatManager.BleMode.mode_advertise);
                        break;
                    default:
                        throw new IllegalStateException("Press illegal button in dialog");
                }
            }
        });

        AlertDialog dialog=builder.create();//获取dialog
        dialog.show();//显示对话框

    }
    //</editor-fold>

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.changeName) {

        } else if (id == R.id.changeInfo) {

        } else if (id == R.id.btOn) {

        } else if (id == R.id.btOff) {

        } else if (id == R.id.mesh_start) {

        } else if (id == R.id.mesh_stop) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        assert drawer != null;
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("BLEisRunning", BLEisRunning);
        getSupportFragmentManager().putFragment(outState,"bleMesh",bleMeshFragment);
        getSupportFragmentManager().putFragment(outState,"userList",userListFragment);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        bleMeshFragment = (BleMeshFragment) getSupportFragmentManager().getFragment(savedInstanceState,"bleMesh");
        userListFragment = (UserListFragment) getSupportFragmentManager().getFragment(savedInstanceState,"userList");
        mChatManager.setServiceBinder(bleMeshFragment.getServiceBinder());
    }

    private void tintSystemBars(final int toolbarFromColor, final int statusbarFromColor,
                                final int toolbarToColor, final int statusbarToColor) {

        ValueAnimator toolbarAnim = ValueAnimator.ofArgb(toolbarFromColor, toolbarToColor);
        ValueAnimator statusbarAnim = ValueAnimator.ofArgb(statusbarFromColor, statusbarToColor);

        statusbarAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                getWindow().setStatusBarColor((Integer) animation.getAnimatedValue());
            }
        });

        toolbarAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                getSupportActionBar().setBackgroundDrawable(new ColorDrawable((Integer) animation.getAnimatedValue()));
            }
        });

        toolbarAnim.setDuration(500).start();
        statusbarAnim.setDuration(500).start();
    }

    //<editor-fold desc="UserListFragmentCallback">
    @Override
    public void onPeerSelected(View identiconView, View usernameView, String peerAddress) {
        Peer peer = mChatManager.getRemotePeer(peerAddress);
        if (peer == null) {
            Timber.w("Could not lookup peer. Cannot show profile");
            return;
        }
        setTitle(peer.getAlias());
        MessageFragment messageFragment = new MessageFragment();
        messageFragment.setDbManager(mChatManager.getDbManager());
        messageFragment.setPeer(mChatManager.getRemotePeer(peerAddress));


        final TransitionSet slideTransition = new TransitionSet();
        slideTransition.addTransition(new Slide());
        slideTransition.setInterpolator(new AccelerateDecelerateInterpolator());
        slideTransition.setDuration(300);
        messageFragment.setEnterTransition(slideTransition);
        messageFragment.setReturnTransition(slideTransition);
        messageFragment.setAllowEnterTransitionOverlap(false);
        messageFragment.setAllowReturnTransitionOverlap(false);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, messageFragment)
                .addToBackStack("profile")
                .commit();

    }

    //</editor-fold>

    //<editor-fold desc="MessageFragmentCallback">
    @Override
    public void onMessageSendRequested(String message,Peer peer) {
        mChatManager.localSentMessage(message.getBytes(),mChatManager.getLocalPeer(),peer);
        mChatManager.sendMessage(message,peer);
    }
    //</editor-fold>
}
