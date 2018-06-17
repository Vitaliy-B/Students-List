package bv.dev.ddapp.studentslist;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.LoaderManager;
import android.content.AsyncTaskLoader;
import android.content.DialogInterface;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor>,
        IDialogFragmentCallback {
    public static final String LOG_TAG = "bv_log";

    private static final int LIST_ITEMS_AT_END = 10;
    private static final int LIST_ITEMS_LIMIT = 20;
    private static final int LIST_ITEMS_LOAD = 20;

    //private static boolean notLoad = true; // not load from network - speed up testing and start up

    private StudentsDB studentsDB;
    private SimpleCursorAdapter sca;
    private ListView lvMain;
    private Cursor coursesCrs;
    private FiltersOnClickListener focl;

    private String filterCourse;
    private int filterMark = 0;
    private int listItemsLimit = LIST_ITEMS_LIMIT;
    private String queryStudent;
    private boolean loadingData = false;
    private boolean sameData = false;
    private boolean loadedAll = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        ChooserDialogFragment cdf = new ChooserDialogFragment();
//        cdf.show(getFragmentManager(), ChooserDialogFragment.class.getSimpleName());
//        Log.d(LOG_TAG, "ChooserDialog shown");

        // bug fix for https://stackoverflow.com/questions/7166363/loadercallbacks-onloadfinished-not-called-if-orientation-change-happens-during-a/47297350#47297350
        LoaderManager loaderMngr = getLoaderManager();
        Log.d(LOG_TAG, "" + loaderMngr);
        postOnCreate();

        lvMain = (ListView) findViewById(R.id.lvMain);
        sca = new SimpleCursorAdapter(this, R.layout.listview_main_item, null,
                new String[]{StudentsDB.TabStudents.COL_NAME_FIRST_NAME,
                        StudentsDB.TabStudents.COL_NAME_LAST_NAME,
                        StudentsDB.TabStudents.COL_NAME_BIRTHDAY},
                new int[] {R.id.tvFName, R.id.tvLName, R.id.tvBirthday},
                0);
        lvMain.setAdapter(sca);
        lvMain.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                // time to load new, list not empty, not currently loading, haven't loaded maximum
                if( (firstVisibleItem + visibleItemCount > totalItemCount - LIST_ITEMS_AT_END)
                        && totalItemCount != 0 && ! loadingData && ! loadedAll) {
                    loadingData = true;
                    sameData = true;
                    listItemsLimit += LIST_ITEMS_LOAD;
                    getLoaderManager().restartLoader(CursorAsyncTaskLoader.QUERY_STUDENTS, null, MainActivity.this);
                }
//                Log.d(LOG_TAG, "lvMain.onScroll(): firstVisible = " + firstVisibleItem
//                        + "; visibleItem = " + visibleItemCount
//                        + "; totalItem = " + totalItemCount
//                        + "; listLimit = " + listItemsLimit);
            }
        });

        focl = new FiltersOnClickListener();
    }

    private void postOnCreate() {
        studentsDB = new StudentsDB(this);

        Toast.makeText(this, "Loading data..", Toast.LENGTH_SHORT).show();
        new DataLoader(this, studentsDB).loadData();

        // check network connection - not needed no more
//        ConnectivityManager connMngr = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
//        if(connMngr != null) {
//            NetworkInfo activeNet = connMngr.getActiveNetworkInfo();
//            if ((activeNet != null && activeNet.isConnected()) /*|| notLoad*/) {
//                Toast.makeText(this, "Loading data..", Toast.LENGTH_SHORT).show();
//                new DataLoader(this, studentsDB /*, notLoad*/).loadData();
//            } else {
//                Log.w(LOG_TAG, "MainActivity : Network is not connected");
//                Toast.makeText(this, "Network is not available", Toast.LENGTH_LONG).show();
//            }
//        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.mm_filter) {
            new FiltersDialogFragment().show(getFragmentManager(), FiltersDialogFragment.class.getSimpleName());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void itemClick(View v) {
        View parent;
        try {
            parent = (View) v.getParent();
            int pos = lvMain.getPositionForView(parent);
            if(pos == AdapterView.INVALID_POSITION) {
                Log.e(LOG_TAG, "Error @ MainActivity : itemClick - invalid position ");
                return;
            }
            Cursor data = (Cursor) sca.getItem(pos);
            queryStudent = data.getString(data.getColumnIndex(StudentsDB.TabStudents.COL_NAME_ID));
            Log.d(LOG_TAG, "Main list itemClick : " + queryStudent);
            getLoaderManager().restartLoader(CursorAsyncTaskLoader.QUERY_COURSES, null, this);
        } catch(ClassCastException | NullPointerException e) {
            Log.e(LOG_TAG, "Error @ MainActivity", e);
            //return; // unnecessary
        }
    }

    //--------------------------
    //LoaderCallbacks
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        //Log.d(LOG_TAG, "LoaderCallbacks.onCreateLoader()");
        return new CursorAsyncTaskLoader(this, id);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        switch(loader.getId()) {
            case CursorAsyncTaskLoader.QUERY_STUDENTS:
                loadingData = false;
                if (sca != null) {
                    Cursor old = sca.getCursor();
                    if(old !=  null && data != null && old.getCount() == data.getCount() && sameData) {
                        loadedAll = true;
                    }
                    sca.swapCursor(data);
                    if(data == null || data.getCount() == 0) {
                        Log.w(LOG_TAG, "onLoadFinished QUERY_STUDENTS : cursor empty or null");
                        Toast.makeText(this, "No data to show..", Toast.LENGTH_LONG).show();
                    }
                }
                break;
            case CursorAsyncTaskLoader.QUERY_COURSES:
                coursesCrs = data;
                // to prevent bug should not show fragment directly
                new Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        new CoursesDialogFragment()
                                .show(getFragmentManager(), CoursesDialogFragment.class.getSimpleName());
                    }
                });
                break;
        }
        Log.d(LOG_TAG, "LoaderCallbacks.onLoadFinished()");
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        switch(loader.getId()) {
            case CursorAsyncTaskLoader.QUERY_STUDENTS:
                if(sca != null) {
                    sca.swapCursor(null);
                }
                break;
            case CursorAsyncTaskLoader.QUERY_COURSES:
                coursesCrs = null;
                break;
        }
        Log.d(LOG_TAG, "LoaderCallbacks.onLoaderReset()");
    }
    //--------------------------

    // custom cursor loader to update list's adapter
    public static class CursorAsyncTaskLoader extends AsyncTaskLoader<Cursor> {
        public static final int QUERY_STUDENTS = 1;
        public static final int QUERY_COURSES = 2;
        private Cursor stored_data = null; // store loaded data
        private int params = 0; // start params
        private MainActivity ma;

        // second could be Bundle
        public CursorAsyncTaskLoader(MainActivity mainActivity, int args) {
            super(mainActivity);
            params = args;
            ma = mainActivity;
            // to init smth else better use application's context: getContext()
            //Log.d(LOG_TAG, "CursorAsyncTaskLoader()");
        }

        @Override
        public Cursor loadInBackground() {
            //Log.d(LOG_TAG, "CursorAsyncTaskLoader.loadInBackground()");
            if(isLoadInBackgroundCanceled() || ma == null) {
                Log.w(LOG_TAG, "CursorAsyncTaskLoader: LoadInBackgroundCanceled");
                return null;
            }

            switch(params) {
                case QUERY_STUDENTS:
                    return ma.studentsDB.queryStudents(ma.filterCourse, ma.filterMark, "" + ma.listItemsLimit);
                case QUERY_COURSES:
                    return ma.studentsDB.queryCourses(ma.queryStudent);
                default:
                    Log.w(LOG_TAG, "CursorAsyncTaskLoader: wrong param");
                    return null;
            }
        }

        @Override
        public void deliverResult(Cursor data) {
            //Log.d(LOG_TAG, "CursorAsyncTaskLoader.deliverResult()");
            if(isReset() && data != null) {
                releaseResources(data);
            }
            Cursor old_data = stored_data;
            stored_data = data;
            if(isStarted()) { // deliver now
                super.deliverResult(data);
            }
            // new result delivered, old is not needed no more
            // so time to release old resources
            if(old_data != null) {
                releaseResources(old_data);
            }
        }


        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        protected void onStopLoading() {
            cancelLoad();
        }

        @Override
        public void onCanceled(Cursor data) {
            super.onCanceled(data);
            releaseResources(data);
        }

        @Override
        protected void onReset() {
            super.onReset();
            // stop if not stopped
            onStopLoading();
            if(stored_data != null) {
                releaseResources(stored_data);
                stored_data = null;
            }
        }

        protected void releaseResources(Cursor crs) {
            if(crs != null) {
                crs.close();
            }
        }
    }

    //-------------------------
    // IDialogFragmentCallback
    @Override
    public Cursor getCursor() {
        return coursesCrs;
    }

    @Override
    public DialogInterface.OnClickListener getDIOCL() {
        return focl;
    }

    @Override
    public void onDone() {
        postOnCreate();
    }

    //-----------------------
    // Dialog fragment
    public static class CoursesDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            IDialogFragmentCallback dfc;
            try {
                dfc = ((IDialogFragmentCallback) getActivity());
            } catch(ClassCastException cce) {
                Log.e(LOG_TAG, "Error @ CoursesDialogFragment", cce);
                return super.onCreateDialog(savedInstanceState);
            }

            // LayoutInflater layInfl = LayoutInflater.from(ma); // does not work properly !!!
            LayoutInflater layInfl = getActivity().getLayoutInflater();
            ListView lv = (ListView) layInfl.inflate(R.layout.dialog_courses_list, null);
            Cursor crs = dfc.getCursor();
            double sum = 0;
            double count = 0;
            if(crs != null && crs.moveToFirst()) {
                do {
                    String name = crs.getString(crs.getColumnIndex(StudentsDB.TabCourses.COL_NAME_NAME));
                    int mark = crs.getInt(crs.getColumnIndex(StudentsDB.TabCourses.COL_NAME_MARK));
                    count++;
                    sum+=mark;
                    View view = layInfl.inflate(R.layout.dialog_courses_list_item, lv, false);
                    ((TextView) view.findViewById(R.id.tvName)).setText(name);
                    ((TextView) view.findViewById(R.id.tvMark)).setText("" + mark);
                    //lv.addView(view); // unsupported
                    lv.addHeaderView(view);
                } while(crs.moveToNext());
            } else {
                Log.e(LOG_TAG, "CoursesDialogFragment : cursor is null or empty");
                Toast.makeText(getActivity(), "No data to show", Toast.LENGTH_LONG).show();
                Dialog dlg = super.onCreateDialog(savedInstanceState);
                dlg.setTitle("No data to show");
                return dlg;
            }
            // adapter required !!
            lv.setAdapter(new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1,
                    new String[] {"Average mark: " + (sum / count)}));

            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.dlg_courses_title)
                    .setView(lv)
                    .setPositiveButton("OK", null)
                    .create();
        }


        @Override
        public void onCancel(DialogInterface dialog) {
            super.onCancel(dialog);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
        }
    }

    //-----------------------
    // Dialog fragment
    public static class FiltersDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            IDialogFragmentCallback dfc;
            try {
                dfc = ((IDialogFragmentCallback) getActivity());
            } catch(ClassCastException cce) {
                Log.e(LOG_TAG, "Error @ CoursesDialogFragment", cce);
                return super.onCreateDialog(savedInstanceState);
            }
            View view = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_filters_view, null, false);
            ((Spinner) view.findViewById(R.id.spinnerCourse)).setSelection(0);
            // to prevent bugs
            AlertDialog.Builder adb;
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                adb = new AlertDialog.Builder(getActivity(), R.style.FilterDialog);
            } else {
                adb = new AlertDialog.Builder(getActivity());
            }
            AlertDialog alertDialog = adb.setTitle(R.string.dlg_filters_title)
                    .setView(view)
                    .setPositiveButton("OK", dfc.getDIOCL())
                    .setNegativeButton("Clear", dfc.getDIOCL())
                    .create();

            // on newer versions produce problems
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                // to fix white background over the dialog (because of custom style)
                alertDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                try {
                    alertDialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
                } catch (NullPointerException npe) {
                    Log.e(LOG_TAG, "", npe);
                }
            }
            return alertDialog;
        }
    }

    private class FiltersOnClickListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            try {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        Spinner spinner = (Spinner) ((AlertDialog) dialog).findViewById(R.id.spinnerCourse);
                        filterCourse = (String) (spinner).getSelectedItem();
                        EditText et = (EditText) ((AlertDialog) dialog).findViewById(R.id.etMark);
                        filterMark = Integer.parseInt(et.getText().toString());
                        listItemsLimit = LIST_ITEMS_LIMIT;
                        sameData = false;
                        loadedAll = false;
                        ((ListView) findViewById(R.id.lvMain)).setSelection(0);
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        filterCourse = null;
                        filterMark = 0;
                        listItemsLimit = LIST_ITEMS_LIMIT;
                        sameData = false;
                        loadedAll = false;
                        ((ListView) findViewById(R.id.lvMain)).setSelection(0);
                        break;
                    default:
                        Log.w(LOG_TAG, "Unknown button @ FiltersOnClickListener() : " + which);
                        return;
                }
                getLoaderManager().restartLoader(CursorAsyncTaskLoader.QUERY_STUDENTS, null, MainActivity.this);
            } catch (NumberFormatException nfe) {
                Log.e(LOG_TAG, "Error @ FiltersOnClickListener : " + nfe.getMessage());
                Toast.makeText(MainActivity.this, "You should enter number !", Toast.LENGTH_LONG).show();
            } catch (ClassCastException cce) {
                Log.e(LOG_TAG, "Error @ FiltersOnClickListener ", cce);
                Toast.makeText(MainActivity.this, "Can't filter data", Toast.LENGTH_LONG).show();
            }
        }
    }

    // Dialog fragment
//    public static class ChooserDialogFragment extends DialogFragment {
//        private IDialogFragmentCallback dfc;
//        private boolean dissmissed = false;
//        @Override
//        public Dialog onCreateDialog(Bundle savedInstanceState) {
//            try {
//                dfc = (IDialogFragmentCallback) getActivity();
//            } catch (ClassCastException cce) {
//                Log.e(LOG_TAG, "Error", cce);
//                Toast.makeText(getActivity(), "Can't show dialog", Toast.LENGTH_LONG).show();
//                return super.onCreateDialog(savedInstanceState);
//            }
//
//            return new AlertDialog.Builder(getActivity())
//                    .setTitle(R.string.dlg_chooser_title)
//                    .setMessage(R.string.dlg_chooser_msg)
//                    .setPositiveButton(R.string.dlg_btn_dload, cdfOcl)
//                    .setNegativeButton(R.string.dlg_btn_use_local, cdfOcl)
//                    .setCancelable(false)
//                    .create();
//            // can't set onCancel / Dismiss listeners, overridden methods instead
//        }
//
//        // heard about bug with onCancel / onDismiss callbacks
//        // but it works correctly even without handling this event
//        // could be deleted
//        @Override
//        public void onCancel(DialogInterface dialog) {
//            super.onCancel(dialog);
//            //Log.d(LOG_TAG, "onCancel()"); // not needed
//            if(! dissmissed) {
//                dissmissed = true;
//                dfc.onDone();
//            }
//        }
//
//        @Override
//        public void onDismiss(DialogInterface dialog) {
//            super.onDismiss(dialog);
//            //Log.d(LOG_TAG, "onDismiss()"); // not needed
//            if(! dissmissed) {
//                dissmissed = true;
//                dfc.onDone();
//            }
//        }
//
//        private DialogInterface.OnClickListener cdfOcl = new DialogInterface.OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//                switch(which) {
//                    case DialogInterface.BUTTON_POSITIVE:
//                        notLoad = false; // Load
//                        break;
//                    case DialogInterface.BUTTON_NEGATIVE:
//                    default:
//                        notLoad = true; // not Load
//                        break;
//
//                }
//            }
//        };
//
//    }

}
