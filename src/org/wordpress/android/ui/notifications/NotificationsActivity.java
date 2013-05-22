package org.wordpress.android.ui.notifications;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;

import android.os.Bundle;
import android.util.Log;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;
import android.content.Intent;
import android.support.v4.content.IntentCompat;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import org.wordpress.android.GCMIntentService;
import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.ui.WPActionBarActivity;
import org.wordpress.android.models.Note;
import static org.wordpress.android.WordPress.*;

import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

public class NotificationsActivity extends WPActionBarActivity {
    public static final String TAG="WPNotifications";
    public static final String NOTE_ID_EXTRA="noteId";
    public static final String FROM_NOTIFICATION_EXTRA="fromNotification";
    public static final String NOTE_REPLY_EXTRA="replyContent";
    public static final String NOTE_INSTANT_REPLY_EXTRA = "instantReply";
    public static final int FLAG_FROM_NOTE=Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP |
                                            Intent.FLAG_ACTIVITY_NEW_TASK |
                                            IntentCompat.FLAG_ACTIVITY_CLEAR_TASK;

    Set<FragmentDetector> fragmentDetectors = new HashSet<FragmentDetector>();

    private NotificationsListFragment mNotesList;
    private MenuItem mRefreshMenuItem;
    private boolean mLoadingMore = false;
    private boolean mFirstLoadComplete = false;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        createMenuDrawer(R.layout.notifications);
        
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        setTitle(getString(R.string.notifications));
        
        
        FragmentManager fm = getSupportFragmentManager();
        mNotesList = (NotificationsListFragment) fm.findFragmentById(R.id.notes_list);
        mNotesList.setNoteProvider(new NoteProvider());
        mNotesList.setOnNoteClickListener(new NoteClickListener());
        
        fragmentDetectors.add(new FragmentDetector(){
            @Override
            public Fragment getFragment(Note note){
                if (note.isCommentType()) {
                    Fragment fragment = new NoteCommentFragment();
                    return fragment;
                }
                return null;
            }
        });
        fragmentDetectors.add(new FragmentDetector(){
           @Override
           public Fragment getFragment(Note note){
               if (note.isSingleLineListTemplate()) {
                   Fragment fragment = new SingleLineListFragment();
                   return fragment;
               }
               return null;
           } 
        });
        fragmentDetectors.add(new FragmentDetector(){
            @Override
            public Fragment getFragment(Note note){
                Log.d(TAG, String.format("Is it a big badge template? %b", note.isBigBadgeTemplate()));
                if (note.isBigBadgeTemplate()) {
                    Fragment fragment = new BigBadgeFragment();
                    return fragment;
                }
                return null;
            }
        });
        
        GCMIntentService.activeNotificationsMap.clear();
        
        launchWithNoteId();
        refreshNotes();
        
    }
    
    
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        
        GCMIntentService.activeNotificationsMap.clear();
        
    }



    /**
     * Detect if Intent has a noteId extra and display that specific note detail fragment
     */
    private void launchWithNoteId(){
        Intent intent = getIntent();
        if (intent.hasExtra(NOTE_ID_EXTRA)) {
            // find it/load it etc
            RequestParams params = new RequestParams();
            params.put("ids", intent.getStringExtra(NOTE_ID_EXTRA));
            restClient.getNotifications(params, new NotesResponseHandler(){
                @Override
                public void onStart(){
                    Log.d(TAG, "Finding note to display!");
                }
                @Override
                public void onSuccess(List<Note> notes){
                    // there should only be one note!
                    if (!notes.isEmpty()) {
                        Note note = notes.get(0);
                        openNote(note);
                    } else {
                        // TODO: Could not load note
                        Toast.makeText(NotificationsActivity.this, getString(R.string.error_generic), Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.equals(mRefreshMenuItem)) {
            refreshNotes();
            return true;
        } else if (item.getItemId() == android.R.id.home){
            FragmentManager fm = getSupportFragmentManager();
            if (fm.getBackStackEntryCount() > 0) {
                popNoteDetail();
                mMenuDrawer.setDrawerIndicatorEnabled(true);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.notifications, menu);
        mRefreshMenuItem = menu.findItem(R.id.menu_refresh);
        if (shouldAnimateRefreshButton) {
            shouldAnimateRefreshButton = false;
            startAnimatingRefreshButton(mRefreshMenuItem);
        }
        return true;
    }

    public void popNoteDetail(){
        FragmentManager fm = getSupportFragmentManager();
        Fragment f = fm.findFragmentById(R.id.commentDetail);
        if (f == null) {
            fm.popBackStack();
        }
    }
    /**
     * Tries to pick the correct fragment detail type for a given note using the
     * fragment detectors
     */
    private Fragment fragmentForNote(Note note){
        Iterator<FragmentDetector> templates = fragmentDetectors.iterator();
        while(templates.hasNext()){
            FragmentDetector detector = templates.next();
            Fragment fragment = detector.getFragment(note);
            if (fragment != null){
                return fragment;
            }
        }
        return null;
    }
    /**
     *  Open a note fragment based on the type of note
     */
    public void openNote(final Note note){
        if (note == null)
            return;
        // if note is "unread" set note to "read"
        if (note.isUnread()) {
            // send a request to mark note as read
            restClient.markNoteAsRead(note, new JsonHttpResponseHandler(){
                @Override
                public void onStart(){
                    Log.d(TAG, "Marking note as read");
                }
                @Override
                public void onFailure(Throwable e, JSONObject response){
                    super.onFailure(e, response);
                    Log.d(TAG, String.format("Failed to mark as read %s", response), e);
                }
                @Override
                public void onFailure(Throwable e, JSONArray response){
                    super.onFailure(e, response);
                    Log.d(TAG, String.format("Failed to mark as read %s", response), e);
                }
                @Override
                public void onFailure(Throwable e, String response){
                    super.onFailure(e, response);
                    Log.d(TAG, String.format("Failed to mark as read %s", response), e);
                }
                @Override
                public void onFailure(Throwable e){
                    super.onFailure(e);
                    Log.d(TAG, "Failed to mark as read %s", e);
                }
                @Override
                public void onSuccess(int status, JSONObject response){
                    Log.d(TAG, String.format("Note is read: %s", response));
                    note.setUnreadCount("0");
                    mNotesList.getNotesAdapter().notifyDataSetChanged();
                }
                @Override
                public void onFinish(){
                    Log.d(TAG, "Completed mark as read request");
                }
            });
        }
        
        FragmentManager fm = getSupportFragmentManager();
        // remove the note detail if it's already on there
        if (fm.getBackStackEntryCount() > 0){
            fm.popBackStack();
        }
        Fragment fragment = fragmentForNote(note);
        if (fragment == null) {
            Log.d(TAG, String.format("No fragment found for %s", note.toJSONObject()));
            return;
        }
        // swap the fragment
        NotificationFragment noteFragment = (NotificationFragment) fragment;
        Intent intent = getIntent();
        if (intent.hasExtra(NOTE_ID_EXTRA) && intent.getStringExtra(NOTE_ID_EXTRA).equals(note.getId())) {
            if (intent.hasExtra(NOTE_REPLY_EXTRA) || intent.hasExtra(NOTE_INSTANT_REPLY_EXTRA)) {
                fragment.setArguments(intent.getExtras());
            }
        }
        noteFragment.setNote(note);
        FragmentTransaction transaction = fm.beginTransaction();
        View container = findViewById(R.id.note_fragment_container);
        transaction.replace(R.id.note_fragment_container, fragment);
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        // only add to backstack if we're removing the list view from the fragment container
        if (container.findViewById(R.id.notes_list) != null) {
            mMenuDrawer.setDrawerIndicatorEnabled(false);
            transaction.addToBackStack(null);
        }
        transaction.commit();
    }
    
    public void moderateComment(String siteId, String commentId, String status, final Note originalNote) {
        WordPress.restClient.moderateComment(siteId, commentId, status, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, JSONObject response) {
                RequestParams params = new RequestParams();
                params.put("ids", originalNote.getId());
                WordPress.restClient.getNotifications(params, new NotesResponseHandler() {
                        @Override
                        public void onStart() {
                            Log.d(TAG, "Finding note to display!");
                        }

                        @Override
                        public void onSuccess(List<Note> notes) {
                            // there should only be one note!
                            if (!notes.isEmpty()) {
                                Note updatedNote = notes.get(0);
                                updateNote(originalNote, updatedNote);
                            }
                        }
                    });
            }

            @Override
            public void onFailure(Throwable e, String response) {
                Log.e(TAG, String.format("Error moderating comment: %s", response), e);
                if (isFinishing())
                    return;
                Toast.makeText(NotificationsActivity.this, getString(R.string.error_moderate_comment), Toast.LENGTH_LONG).show();
                FragmentManager fm = getSupportFragmentManager();
                NoteCommentFragment f = (NoteCommentFragment) fm.findFragmentById(R.id.note_fragment_container);
                if (f != null) {
                    f.animateModeration(false);
                }
            }
            @Override
            public void onFailure(Throwable e, JSONObject response){
                this.onFailure(e, response.toString());
            }
            @Override
            public void onFailure(Throwable e, JSONArray response){
                this.onFailure(e, response.toString());
            }
            @Override
            public void onFailure(Throwable e){
                this.onFailure(e, "");
            }
        });
    }
    
    public void updateNote(Note originalNote, Note updatedNote) {
        if (isFinishing())
            return;
        int position = mNotesList.getNotesAdapter().getPosition(originalNote);
        if (position >= 0) {
            mNotesList.getNotesAdapter().remove(originalNote);
            mNotesList.getNotesAdapter().insert(updatedNote, position);
            mNotesList.getNotesAdapter().notifyDataSetChanged();
            // Update comment detail fragment if we're still viewing the same note
            if (position == mNotesList.getListView().getCheckedItemPosition()) {
                FragmentManager fm = getSupportFragmentManager();
                NoteCommentFragment f = (NoteCommentFragment) fm.findFragmentById(R.id.note_fragment_container);
                if (f != null) {
                    f.setNote(updatedNote);
                    f.onStart();
                    f.animateModeration(false);
                }
            }
        }
    }

    public void refreshNotes(){
        restClient.getNotifications(new NotesResponseHandler(){
            @Override
            public void onStart(){
                super.onStart();
                mFirstLoadComplete = false;
                shouldAnimateRefreshButton = true;
                startAnimatingRefreshButton(mRefreshMenuItem);
            }
            @Override
            public void onSuccess(List<Note> notes){
                final NotificationsListFragment.NotesAdapter adapter = mNotesList.getNotesAdapter();
                adapter.clear();
                adapter.addAll(notes);
                adapter.notifyDataSetChanged();
                // mark last seen timestampe
                if (!notes.isEmpty()) {
                    updateLastSeen(notes.get(0).getTimestamp());                    
                }
            }
            @Override
            public void onFinish(){
                super.onFinish();
                mFirstLoadComplete = true;
                stopAnimatingRefreshButton(mRefreshMenuItem);
            }
            @Override
            public void showError(){
                //We need to show an error message? and remove the loading indicator from the list?
                final NotificationsListFragment.NotesAdapter adapter = mNotesList.getNotesAdapter();
                adapter.clear();
                adapter.addAll(new ArrayList<Note>());
                adapter.notifyDataSetChanged();
                Toast.makeText(NotificationsActivity.this, getString(R.string.error_refresh), Toast.LENGTH_LONG).show();
            }
        });
    }
    protected void updateLastSeen(String timestamp){
        restClient.markNotificationsSeen(timestamp, new JsonHttpResponseHandler(){
            @Override
            public void onSuccess(int status, JSONObject response){
                Log.d(TAG, String.format("Set last seen time %s", response));
            }
            @Override
            public void onFailure(Throwable e, JSONObject response){
                Log.d(TAG, String.format("Failed to set last seen time %s", response), e);
            }
            @Override
            public void onFailure(Throwable e, JSONArray response){
                Log.d(TAG, String.format("Failed to set last seen time %s", response), e);
            }
            @Override
            public void onFailure(Throwable e, String response){
                Log.d(TAG, String.format("Failed to set last seen time %s", response), e);
            }
            @Override
            public void onFailure(Throwable e){
                Log.d(TAG, "Failed to set last seen time %s", e);
            }
        });
    }
    public void requestNotesBefore(Note note){
        RequestParams params = new RequestParams();
        Log.d(TAG, String.format("Requesting more notes before %s", note.queryJSON("timestamp", "")));
        params.put("before", note.queryJSON("timestamp", ""));
        restClient.getNotifications(params, new NotesResponseHandler(){
            @Override
            public void onSuccess(List<Note> notes){
                NotificationsListFragment.NotesAdapter adapter = mNotesList.getNotesAdapter();
                adapter.addAll(notes);
                adapter.notifyDataSetChanged();
            }
        });
    }

    private class NoteProvider implements NotificationsListFragment.NoteProvider {
        @Override
        public void onRequestMoreNotifications(ListView notesList, ListAdapter notesAdapter){
            if (mFirstLoadComplete && !mLoadingMore) {
                NotificationsListFragment.NotesAdapter adapter = mNotesList.getNotesAdapter();
                if (adapter.getCount() > 0) {
                    Note lastNote = adapter.getItem(adapter.getCount()-1);
                    requestNotesBefore(lastNote);
                }
            }
        }
    }
    
    private class NoteClickListener implements NotificationsListFragment.OnNoteClickListener {
        @Override
        public void onClickNote(Note note){
            openNote(note);
        }
    }
    
    public class NotesResponseHandler extends JsonHttpResponseHandler {
        
        @Override
        public void onStart(){
            mLoadingMore = true;
        }
        
        @Override
        public void onFinish(){
            mLoadingMore = false;
        }
        
        public void onSuccess(List<Note> notes){};
      
        @Override
        public void onSuccess(int statusCode, JSONObject response){
            List<Note> notes;
            try {
                JSONArray notesJSON = response.getJSONArray("notes");
                notes = new ArrayList<Note>(notesJSON.length());
                for (int i=0; i<notesJSON.length(); i++) {
                    Note n = new Note(notesJSON.getJSONObject(i));
                    notes.add(n);
                }
           } catch (JSONException e) {
               Log.e(TAG, "Success, but did not receive any notes", e);
               onFailure(e, response);
               return;
           }
           onSuccess(notes);
        }
        @Override
        public void onFailure(Throwable e, JSONObject response){
            Log.d(TAG, String.format("Error retrieving notes: %s", response), e);
            mLoadingMore = false;
            this.showError();
        }
        @Override
        public void onFailure(Throwable e, JSONArray response){
            Log.d(TAG, String.format("Error retrieving notes: %s", response), e);
            mLoadingMore = false;
            this.showError();
        }
        @Override
        public void onFailure(Throwable e, String response){
            Log.d(TAG, String.format("Error retrieving notes: %s", response), e);
            mLoadingMore = false;
            this.showError();
        }
        @Override
        public void onFailure(Throwable e){
            Log.d(TAG,"Error retrieving notes: %s", e);
            mLoadingMore = false;
            this.showError();
        }
        
        public void showError(){
            Toast.makeText(NotificationsActivity.this, getString(R.string.error_generic), Toast.LENGTH_LONG).show();
        }
    }
    
    private abstract class FragmentDetector {
        abstract public Fragment getFragment(Note note);
    }
}
