package bv.dev.ddapp.studentslist;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.LoaderManager;
import android.app.ProgressDialog;
import android.content.Loader;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.JsonReader;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

// Loads data from web server
public class DataLoader {
    private static final String LOG_TAG = MainActivity.LOG_TAG;
    // local copy also available \/
    private static final String DATA_URL = "https://ddapp-sfa-api-dev.azurewebsites.net/api/test/students";
    private static final int updateProgressAfter = 1;

//    private boolean notLoad = true; // not load from network - speed up testing and start up

    private Activity activity;
    private StudentsDB studentsDB;

    public DataLoader(Activity activity, StudentsDB studentsDB /*, boolean notLoad*/) {
        this.activity = activity;
        this.studentsDB = studentsDB;
//        this.notLoad = notLoad;
    }

    public void loadData() {
        new LoadDataAsyncTask().execute(DATA_URL);
    }

    private class LoadDataAsyncTask extends AsyncTask<String, Long, Boolean> {
        private StringBuilder sbMsg = new StringBuilder();
        private int responseCode = -2;
        private String responseMsg;
        private long recordsProcessed = 0;
        private ProgressDialogFragment dlgProgressFragm;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dlgProgressFragm = new ProgressDialogFragment();
            dlgProgressFragm.show(activity.getFragmentManager(), ProgressDialogFragment.class.getSimpleName());
        }

        @Override
        protected Boolean doInBackground(String... params) {
//            if(notLoad) {
//                return true;
//            }

            if (params == null || params.length < 1) {
                Log.e(LOG_TAG, "Error @ LoadDataAsyncTask : params not set");
                sbMsg.append("Internal error");
                return false;
            }

            try { // for DB
                if (! studentsDB.isEmpty()) {
                    return true;
                }
                HttpURLConnection conn = null;
                JsonReader jsonRdr = null;
                InputStream istream = null;
                try { // download from network
                    URL url = new URL(params[0]);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setReadTimeout(10000);
                    conn.setConnectTimeout(15000);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.connect();
                    responseCode = conn.getResponseCode();
                    responseMsg = conn.getResponseMessage();
                    Log.i(LOG_TAG, "Load Data : ResponseCode = " + responseCode);
                    Log.i(LOG_TAG, "Load Data : ResponseMessage = " + responseMsg);
                    Log.i(LOG_TAG, "Load Data : ContentType = " + conn.getContentType());
                    istream = conn.getInputStream();
                } catch (IOException ioe) {
                    sbMsg.append("Error: ")
                            .append(ioe.getMessage()).append("; ")
                            .append(responseMsg)
                            .append(" (").append(responseCode).append(")");
                    Log.e(LOG_TAG, "Error @ LoadData", ioe);
                    //return false; // can't load from network - load from file
                    istream = activity.getResources().openRawResource(R.raw.example_server_response);
                }

                try { // read
                    jsonRdr = new JsonReader(new InputStreamReader(istream)); // can pass charset
                    studentsDB.clearTables();
                    readJSONArray(jsonRdr);
                    return true;
                } catch (IOException ioe) {
                    sbMsg.append("Error: ").append(ioe.getMessage());
                    Log.e(LOG_TAG, "Error @ LoadData", ioe);
                    return false;
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                    if (jsonRdr != null) {
                        try {
                            jsonRdr.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            } catch (SQLiteException sqle) {
                sbMsg.append("DataBase error ");
                /*.append(sqle.getMessage());*/ // redundant
                Log.e(LOG_TAG, "Error @ LoadData", sqle);
                return false;
            }
        }

        // structure for temporal storage
        private class Course {
            public String name;
            public int mark;

            @Override
            public String toString() {
                return "["+ name + "; " + mark + "]@" + Integer.toHexString(hashCode());
            }
        }

        private void readJSONArray(JsonReader jsonRdr) throws IOException {
            jsonRdr.beginArray();
            while(jsonRdr.hasNext()) {
                // structure element data
                String id = null;
                String firstName = null;
                String lastName = null;
                long birthday = -1;
                ArrayList<Course> alCourses = null;

                jsonRdr.beginObject();
                while(jsonRdr.hasNext()) {
                    String name = jsonRdr.nextName();
                    // for NULLable objects can use
                    //jsonRdr.peek() == JsonToken.NULL
                    switch(name) {
                        case "id":
                            id = jsonRdr.nextString();
                            break;
                        case "firstName":
                            firstName = jsonRdr.nextString();
                            break;
                        case "lastName":
                            lastName = jsonRdr.nextString();
                            break;
                        case "birthday":
                            birthday = jsonRdr.nextLong();
                            break;
                        case "courses":
                            alCourses = readJSONArrayCourses(jsonRdr);
                            break;
                        default:
                            jsonRdr.skipValue();
                            break;
                    }
                }
                jsonRdr.endObject();

                /* Debug code
                StringBuilder sbItem = new StringBuilder();
                String sep = "; "; // separator
                sbItem.append("{").append(id).append(sep).append(firstName).append(sep)
                        .append(lastName).append(sep).append(birthday).append(sep)
                        .append(alCourses).append("}");
                Log.d(LOG_TAG, sbItem.toString());
                /**/

                // insert to DB
                if(id != null) {
                    studentsDB.insertStudent(id, firstName, lastName, birthday);
                    if(alCourses != null) {
                        for (Course course : alCourses) {
                            studentsDB.insertCourse(course.name, course.mark, id);
                        }
                    }
                    if(++recordsProcessed % updateProgressAfter == 0) {
                        publishProgress(recordsProcessed);
                    }
                }

            }
            jsonRdr.endArray();
        }

        private ArrayList<Course> readJSONArrayCourses(JsonReader jsonRdr) throws IOException {
            ArrayList<Course> alCourses = new ArrayList<>();
            jsonRdr.beginArray();
            while(jsonRdr.hasNext()) { // read array
                Course course = new Course();

                jsonRdr.beginObject();
                while(jsonRdr.hasNext()) { // read element
                    String element = jsonRdr.nextName();
                    switch(element) {
                        case "name":
                            course.name = jsonRdr.nextString();
                            break;
                        case "mark":
                            course.mark = jsonRdr.nextInt();
                            break;
                        default:
                            jsonRdr.skipValue();
                            break;
                    }
                }
                jsonRdr.endObject();

                alCourses.add(course);
                //course = new Course(); // performed at the start
            }
            jsonRdr.endArray();

            return alCourses;
        }

        @Override
        protected void onProgressUpdate(Long... values) {
            super.onProgressUpdate(values);
            if(values != null && values.length > 0) {
                /* old code
                String msg = "Records processed : " + values[0];
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                */

                Log.i(LOG_TAG, "Records processed : " + values[0]);

                if(dlgProgressFragm != null) {
                    try {
                        ProgressDialog pd = (ProgressDialog) dlgProgressFragm.getDialog();
                        if(pd != null) {
                            pd.setMessage(activity.getString(R.string.dlg_progress_msg) + " " + values[0]);
                        }
                    } catch (ClassCastException cce) {
                        Log.e(LOG_TAG, "Error @ DataLoader", cce);
                    }
                }
            } else {
                Log.e(LOG_TAG, "Error @ DataLoader.onProgressUpdate() : Wrong args");
            }
        }

        @Override
        protected void onPostExecute(Boolean res) {
            super.onPostExecute(res);
            if(!res) {
                Toast.makeText(activity, sbMsg, Toast.LENGTH_LONG).show();
                Log.e(LOG_TAG, "DataLoader : Error");
            } else {
                //Toast.makeText(activity, "Data loaded", Toast.LENGTH_SHORT).show();
                Log.i(LOG_TAG, "DataLoader : Done");
            }

            if(dlgProgressFragm != null) {
                dlgProgressFragm.dismiss();
                dlgProgressFragm = null;
            }

            try { /* see also https://stackoverflow.com/questions/7166363/loadercallbacks-onloadfinished-not-called-if-orientation-change-happens-during-a/47297350#47297350 */
                activity.getLoaderManager().restartLoader(MainActivity.CursorAsyncTaskLoader.QUERY_STUDENTS,
                        null, (LoaderManager.LoaderCallbacks) activity);
            } catch(ClassCastException cce) {
                Log.e(LOG_TAG, "Error @ DataLoader", cce);
                Toast.makeText(activity, "Can't init list", Toast.LENGTH_LONG).show();
            }
        }

        @Override
        protected void onCancelled(Boolean res) {
            super.onCancelled(res);
            String msg = "Data loading canceled";
            Log.i(LOG_TAG, msg);
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();

            if(dlgProgressFragm != null) {
                dlgProgressFragm.dismiss();
                dlgProgressFragm = null;
            }
        }
    }

    // Dialog to show loading progress
    public static class ProgressDialogFragment extends DialogFragment {
        @NonNull @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            ProgressDialog pd = new ProgressDialog(getActivity());
            pd.setIcon(android.R.drawable.ic_dialog_info);
            pd.setTitle(R.string.dlg_progress_title);
            pd.setMessage(getString(R.string.dlg_progress_msg)); //REQUIRED TO BE ABLE TO UPDATE
            pd.setIndeterminate(true);
            pd.setCancelable(false);
            pd.setCanceledOnTouchOutside(false);
            return pd;
        }
    }
}
