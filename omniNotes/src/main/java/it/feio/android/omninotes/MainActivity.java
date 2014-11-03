package it.feio.android.omninotes;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.view.MenuItem;
import android.widget.DatePicker;
import android.widget.TimePicker;

import java.util.ArrayList;
import java.util.HashMap;

import de.keyboardsurfer.android.widget.crouton.Crouton;
import it.feio.android.omninotes.async.DeleteNoteTask;
import it.feio.android.omninotes.async.UpdaterTask;
import it.feio.android.omninotes.db.DbHelper;
import it.feio.android.omninotes.models.Attachment;
import it.feio.android.omninotes.models.Category;
import it.feio.android.omninotes.models.Note;
import it.feio.android.omninotes.models.PasswordValidator;
import it.feio.android.omninotes.models.listeners.OnPushBulletReplyListener;
import it.feio.android.omninotes.utils.AlphaManager;
import it.feio.android.omninotes.utils.AppTourHelper;
import it.feio.android.omninotes.utils.Constants;
import roboguice.util.Ln;

public class MainActivity extends BaseActivity implements OnDateSetListener, OnTimeSetListener, OnPushBulletReplyListener {

	public final String FRAGMENT_DRAWER_TAG = "fragment_drawer";
	public final String FRAGMENT_LIST_TAG = "fragment_list";
	public final String FRAGMENT_DETAIL_TAG = "fragment_detail";
	public final String FRAGMENT_SKETCH_TAG = "fragment_sketch";

    private static MainActivity instance;
	private FragmentManager mFragmentManager;

    public boolean loadNotesSync = Constants.LOAD_NOTES_SYNC;

	public Uri sketchUri;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        instance = this;

        // This method starts the bootstrap chain.
		requestShowCaseViewVisualization();

		// Launching update task
		UpdaterTask task = new UpdaterTask(this);
		task.execute();
	}



	private void checkPassword() {
		if (prefs.getString(Constants.PREF_PASSWORD, null) != null
				&& prefs.getBoolean("settings_password_access", false)) {
			requestPassword(this, new PasswordValidator() {
				@Override
				public void onPasswordValidated(boolean passwordConfirmed) {
					if (passwordConfirmed) {
						init();
					} else {
						finish();
					}
				}
			});
		} else {
			init();
		}
	}


    public static MainActivity getInstance() {
        return instance;
    }


	private void init() {
		mFragmentManager = getSupportFragmentManager();

        NavigationDrawerFragment mNavigationDrawerFragment = (NavigationDrawerFragment) mFragmentManager.findFragmentById(R.id.navigation_drawer);
		if (mNavigationDrawerFragment == null) {
			FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
			fragmentTransaction.replace(R.id.navigation_drawer, new NavigationDrawerFragment(), FRAGMENT_DRAWER_TAG).commit();
		}

		if (mFragmentManager.findFragmentByTag(FRAGMENT_LIST_TAG) == null) {
			FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
			fragmentTransaction.add(R.id.fragment_container, new ListFragment(), FRAGMENT_LIST_TAG).commit();
		}

		// Handling of Intent actions
		handleIntents();
	}



	@Override
	public void onLowMemory() {
		Ln.w("Low memory, bitmap cache will be cleaned!");
		OmniNotes.getBitmapCache().evictAll();
		super.onLowMemory();
	}



	@Override
	protected void onNewIntent(Intent intent) {
		if (intent.getAction() == null) {
			intent.setAction(Constants.ACTION_START_APP);
		}
		setIntent(intent);
		handleIntents();
		Ln.d("onNewIntent");
		super.onNewIntent(intent);
	}



	public MenuItem getSearchMenuItem() {
		Fragment f = checkFragmentInstance(R.id.fragment_container, ListFragment.class);
		if (f != null) {
			return ((ListFragment)f).getSearchMenuItem();
		} else {
			return null;
		}
	}

	public void editTag(Category tag) {
		Fragment f = checkFragmentInstance(R.id.fragment_container, ListFragment.class);
		if (f != null) {
			((ListFragment)f).editCategory(tag);
		}
	}

	public void initNotesList(Intent intent) {
		Fragment f = checkFragmentInstance(R.id.fragment_container, ListFragment.class);
		if (f != null) {
//			List view is set as transparent to perform a fade in animation and give a smoother sensation
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				AlphaManager.setAlpha(findViewById(R.id.notes_list), 0);
			}
			((ListFragment)f).toggleSearchLabel(false);
			((ListFragment)f).initNotesList(intent);
		}
	}

	public void commitPending() {
		Fragment f = checkFragmentInstance(R.id.fragment_container, ListFragment.class);
		if (f != null) {
			((ListFragment)f).commitPending();
		}
	}

	public void editNote(Note note) {
		Fragment f = checkFragmentInstance(R.id.fragment_container, ListFragment.class);
		if (f != null) {
			((ListFragment)f).editNote(note);
		}
	}

	public void initNavigationDrawer() {
		Fragment f = checkFragmentInstance(R.id.navigation_drawer, NavigationDrawerFragment.class);
		if (f != null) ((NavigationDrawerFragment) f).initNavigationDrawer();
	}


	/**
	 * Checks if allocated fragment is of the required type and then returns it or returns null
	 */
	private Fragment checkFragmentInstance(int id, Object instanceClass) {
		Fragment result = null;
		if (mFragmentManager != null) {
			Fragment fragment = mFragmentManager.findFragmentById(id);
			if (instanceClass.equals(fragment.getClass())) {
				result = fragment;
			}
		}
		return result;
	}


	/*
	 * (non-Javadoc)
	 * @see android.support.v7.app.ActionBarActivity#onBackPressed()
	 *
	 * Overrides the onBackPressed behavior for the attached fragments
	 */
	public void onBackPressed() {

		Fragment f;

		// SketchFragment
		f = checkFragmentInstance(R.id.fragment_container, SketchFragment.class);
		if (f != null) {
			((SketchFragment)f).save();

			// Removes forced portrait orientation for this fragment
			setRequestedOrientation(
	                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

			mFragmentManager.popBackStack();
			return;
		}

		// DetailFragment
		f = checkFragmentInstance(R.id.fragment_container, DetailFragment.class);
		if (f != null) {
			((DetailFragment)f).goBack = true;
			((DetailFragment)f).saveAndExit((DetailFragment)f);
			return;
		}

		// ListFragment
		f = checkFragmentInstance(R.id.fragment_container, ListFragment.class);
		if (f != null) {
			// Before exiting from app the navigation drawer is opened
			if (prefs.getBoolean("settings_navdrawer_on_exit",  false) && getDrawerLayout() != null && !getDrawerLayout().isDrawerOpen(GravityCompat.START)) {
				getDrawerLayout().openDrawer(GravityCompat.START);
			} else if (!prefs.getBoolean("settings_navdrawer_on_exit",  false) && getDrawerLayout() != null && getDrawerLayout().isDrawerOpen(GravityCompat.START)) {
				getDrawerLayout().closeDrawer(GravityCompat.START);
			} else {
				super.onBackPressed();
			}
			return;
		}
		super.onBackPressed();
	}



	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString("navigationTmp", navigationTmp);
	}


	@Override
	protected void onPause() {
		super.onPause();
		Crouton.cancelAllCroutons();
	}


	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}


	public DrawerLayout getDrawerLayout() {
        return ((DrawerLayout) findViewById(R.id.drawer_layout));
	}


	public ActionBarDrawerToggle getDrawerToggle() {
		if (mFragmentManager != null && mFragmentManager.findFragmentById(R.id.navigation_drawer) != null) {
			return ((NavigationDrawerFragment)mFragmentManager.findFragmentById(R.id.navigation_drawer)).mDrawerToggle;
		} else {
			return null;
		}
	}


	/**
	 * Finishes multiselection mode started by ListFragment
	 */
	public void finishActionMode() {
		ListFragment fragment = (ListFragment)mFragmentManager.findFragmentByTag(FRAGMENT_LIST_TAG);
		if (fragment != null) {
			fragment.finishActionMode();
		}
	}


	private void handleIntents() {
		Intent i = getIntent();

		if (i.getAction() == null) return;

		if (Constants.ACTION_RESTART_APP.equals(i.getAction())) {
			OmniNotes.restartApp(getApplicationContext());
		}

		// Action called from widget
		if (Constants.ACTION_SHORTCUT.equals(i.getAction())
			|| Constants.ACTION_NOTIFICATION_CLICK.equals(i.getAction())
			|| Constants.ACTION_WIDGET.equals(i.getAction())
			|| Constants.ACTION_WIDGET_TAKE_PHOTO.equals(i.getAction())

			|| ( ( Intent.ACTION_SEND.equals(i.getAction())
					|| Intent.ACTION_SEND_MULTIPLE.equals(i.getAction())
					|| Constants.INTENT_GOOGLE_NOW.equals(i.getAction()) )
					&& i.getType() != null)

			|| i.getAction().contains(Constants.ACTION_NOTIFICATION_CLICK)

					) {
			Note note = i.getParcelableExtra(Constants.INTENT_NOTE);
			if (note == null) {
				note = DbHelper.getInstance(this).getNote(i.getIntExtra(Constants.INTENT_KEY, 0));
			}

			// Checks if the same note is already opened to avoid to open again
			Fragment detailFragment = mFragmentManager.findFragmentByTag(FRAGMENT_DETAIL_TAG);
			if (note != null && detailFragment != null && detailFragment.isVisible()) {
				Note openedNote = ((DetailFragment)detailFragment).getCurrentNote();
				if (note.get_id() == openedNote.get_id()) {
					return;
				}
			}

			// Empty note instantiation
			if (note == null) {
				note = new Note();
			}

			switchToDetail(note);
		}

		// Tag search
		if (Intent.ACTION_VIEW.equals(i.getAction())) {
			switchToList();
		}
	}





	public void switchToList() {
		FragmentTransaction transaction = mFragmentManager.beginTransaction();
		animateTransition(transaction, TRANSITION_HORIZONTAL);
		ListFragment mListFragment = new ListFragment();
		transaction.replace(R.id.fragment_container, mListFragment, FRAGMENT_LIST_TAG).addToBackStack(FRAGMENT_DETAIL_TAG).commitAllowingStateLoss();
		if (getDrawerToggle() != null) {
			getDrawerToggle().setDrawerIndicatorEnabled(false);
		}
		mFragmentManager.getFragments();
	}


	public void switchToDetail(Note note) {
		FragmentTransaction transaction = mFragmentManager.beginTransaction();
		animateTransition(transaction, TRANSITION_HORIZONTAL);
		DetailFragment mDetailFragment = new DetailFragment();
		Bundle b = new Bundle();
		b.putParcelable(Constants.INTENT_NOTE, note);
		mDetailFragment.setArguments(b);
		transaction.replace(R.id.fragment_container, mDetailFragment, FRAGMENT_DETAIL_TAG).addToBackStack(FRAGMENT_LIST_TAG).commitAllowingStateLoss();
		if (getDrawerToggle() != null) {
			getDrawerToggle().setDrawerIndicatorEnabled(false);
		}
	}



	/**
	 * Notes sharing
	 */
	public void shareNote(Note note) {

		String titleText = note.getTitle();

		String contentText = titleText
								+ System.getProperty("line.separator")
								+ note.getContent();


		Intent shareIntent = new Intent();
		// Prepare sharing intent with only text
		if (note.getAttachmentsList().size() == 0) {
			shareIntent.setAction(Intent.ACTION_SEND);
			shareIntent.setType("text/plain");
			shareIntent.putExtra(Intent.EXTRA_SUBJECT, titleText);
			shareIntent.putExtra(Intent.EXTRA_TEXT, contentText);

			// Intent with single image attachment
		} else if (note.getAttachmentsList().size() == 1) {
			shareIntent.setAction(Intent.ACTION_SEND);
			shareIntent.setType(note.getAttachmentsList().get(0).getMime_type());
			shareIntent.putExtra(Intent.EXTRA_STREAM, note.getAttachmentsList().get(0).getUri());
			shareIntent.putExtra(Intent.EXTRA_SUBJECT, titleText);
			shareIntent.putExtra(Intent.EXTRA_TEXT, contentText);

			// Intent with multiple images
		} else if (note.getAttachmentsList().size() > 1) {
			shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
			ArrayList<Uri> uris = new ArrayList<Uri>();
			// A check to decide the mime type of attachments to share is done here
			HashMap<String, Boolean> mimeTypes = new HashMap<String, Boolean>();
			for (Attachment attachment : note.getAttachmentsList()) {
				uris.add(attachment.getUri());
				mimeTypes.put(attachment.getMime_type(), true);
			}
			// If many mime types are present a general type is assigned to intent
			if (mimeTypes.size() > 1) {
				shareIntent.setType("*/*");
			} else {
				shareIntent.setType((String) mimeTypes.keySet().toArray()[0]);
			}

			shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
			shareIntent.putExtra(Intent.EXTRA_SUBJECT, titleText);
			shareIntent.putExtra(Intent.EXTRA_TEXT, contentText);
		}

		startActivity(Intent.createChooser(shareIntent, getResources().getString(R.string.share_message_chooser)));
	}




	/**
	 * Single note permanent deletion
	 *
	 * @param note
	 *            Note to be deleted
	 */
	@SuppressLint("NewApi")
	public void deleteNote(Note note) {

		// Saving changes to the note
		DeleteNoteTask deleteNoteTask = new DeleteNoteTask(getApplicationContext());
		// Removed parallel computation to avoid concurrency on pool errors
//		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
//			deleteNoteTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, note);
//		} else {
			deleteNoteTask.execute(note);
//		}

		// Informs about update
		Ln.d("Deleted permanently note with id '" + note.get_id() + "'");
	}


    /**
     * Showcase view displaying request for first launch
     */
    private void requestShowCaseViewVisualization() {

        if (AppTourHelper.mustRun(getApplicationContext())) {
            final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
            alertDialogBuilder
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.tour_request_start)
                    .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            checkPassword();
                        }
                    }).setNegativeButton(R.string.not_now, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    AppTourHelper.complete(getApplicationContext());
                    checkPassword();
                }
            });
            alertDialogBuilder.create().show();
        } else {
            checkPassword();
        }
    }



	@Override
	public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
		DetailFragment f = (DetailFragment) mFragmentManager.findFragmentByTag(FRAGMENT_DETAIL_TAG);
		if (f != null && f.isAdded()) {
			f.onTimeSetListener.onTimeSet(view, hourOfDay, minute);
		}
	}



	@Override
	public void onDateSet(DatePicker view, int year, int monthOfYear,
			int dayOfMonth) {
		DetailFragment f = (DetailFragment) mFragmentManager.findFragmentByTag(FRAGMENT_DETAIL_TAG);
		if (f != null && f.isAdded()) {
			f.onDateSetListener.onDateSet(view, year, monthOfYear, dayOfMonth);
		}
	}


    @Override
    public void onPushBulletReply(final String message) {
        final DetailFragment df = (DetailFragment) mFragmentManager.findFragmentByTag(FRAGMENT_DETAIL_TAG);
        if (df != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    df.appendToContentViewText(message);
                }
            });
        }
    }
}
