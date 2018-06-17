package bv.dev.ddapp.studentslist;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import java.text.SimpleDateFormat;

public class StudentsDB {
    private static final String DB_NAME = "StudentsDB";
    private static final int DB_VERSION = 1;

    //------------------------------
    // TABLES
    public static final class TabStudents {
        public static final String TAB_NAME_STUDENTS = "students";
        public static final String COL_NAME_ID = "_id";
        public static final String COL_NAME_FIRST_NAME = "firstName";
        public static final String COL_NAME_LAST_NAME = "lastName";
        public static final String COL_NAME_BIRTHDAY = "birthday";

        private static final String QUERY_CREATE_TAB_STUDENTS = "CREATE TABLE " + TAB_NAME_STUDENTS
                + " ( " + COL_NAME_ID + " TEXT NOT NULL PRIMARY KEY, "
                + COL_NAME_FIRST_NAME + " TEXT, "
                + COL_NAME_LAST_NAME + " TEXT, "
                //+ COL_NAME_BIRTHDAY + " INTEGER );" //old
                + COL_NAME_BIRTHDAY + " TEXT );";

        private static final String QUERY_DROP_TAB_IF_EXISTS = "DROP TABLE IF EXISTS " + TAB_NAME_STUDENTS + ";";
    }

    public static final class TabCourses {
        public static final String TAB_NAME_COURSES = "courses";
        public static final String COL_NAME_ID = "_id";
        public static final String COL_NAME_NAME = "name";
        public static final String COL_NAME_MARK = "mark";
        public static final String COL_NAME_STUDENT = "student";

        private static final String QUERY_CREATE_TAB_COURSES = "CREATE TABLE " + TAB_NAME_COURSES
                + "( " + COL_NAME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                + COL_NAME_NAME + " TEXT, "
                + COL_NAME_MARK + " INTEGER, "
                + COL_NAME_STUDENT + " TEXT NOT NULL, "
                + "FOREIGN KEY (" + COL_NAME_STUDENT + ") REFERENCES "
                + TabStudents.TAB_NAME_STUDENTS + " (" + TabStudents.COL_NAME_ID + "));";
        private static final String QUERY_DROP_TAB_IF_EXISTS = "DROP TABLE IF EXISTS " + TAB_NAME_COURSES + ";";
    }
    //------------------------------

    //private Context cntx; //not needed
    private StudentsDBHelper studentsDBHelper;
    private SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy.MM.dd");

    public StudentsDB(Context context) {
        //cntx = context; // not needed
        studentsDBHelper = new StudentsDBHelper(context, DB_NAME, null, DB_VERSION);
    }

    public void clearTables() throws SQLiteException {
        SQLiteDatabase db = studentsDBHelper.getWritableDatabase();
        db.execSQL(TabCourses.QUERY_DROP_TAB_IF_EXISTS);
        db.execSQL(TabStudents.QUERY_DROP_TAB_IF_EXISTS);

        db.execSQL(TabStudents.QUERY_CREATE_TAB_STUDENTS);
        db.execSQL(TabCourses.QUERY_CREATE_TAB_COURSES);
    }

    public boolean isEmpty() throws SQLiteException {
        SQLiteDatabase db = studentsDBHelper.getReadableDatabase();
        Cursor crs = db.rawQuery("SELECT COUNT(*) FROM " + TabStudents.TAB_NAME_STUDENTS, null);
        int count = 0;
        if(crs.moveToFirst()) {
            count = crs.getInt(0); // count(*)
        }
        crs.close();
        return count <= 0; // count(*)
    }

    // Returns long - the row ID of the newly inserted row, or -1 if an error occurred
    public long insertStudent(String id, String firstName, String lastName, long birthday)
            throws SQLiteException {
        SQLiteDatabase db = studentsDBHelper.getWritableDatabase();
        ContentValues cv = new ContentValues(4);
        cv.put(TabStudents.COL_NAME_ID, id);
        cv.put(TabStudents.COL_NAME_FIRST_NAME, firstName);
        cv.put(TabStudents.COL_NAME_LAST_NAME, lastName);
        //cv.put(TabStudents.COL_NAME_BIRTHDAY, birthday); // old
        cv.put(TabStudents.COL_NAME_BIRTHDAY, dateFormatter.format(birthday));
        return db.insert(TabStudents.TAB_NAME_STUDENTS, null, cv);
    }

    // Returns long - the row ID of the newly inserted row, or -1 if an error occurred
    public long insertCourse(String name, int mark, String studentId)
            throws SQLiteException {
        SQLiteDatabase db = studentsDBHelper.getWritableDatabase();
        ContentValues cv = new ContentValues(3);
        cv.put(TabCourses.COL_NAME_NAME, name);
        cv.put(TabCourses.COL_NAME_MARK, mark);
        cv.put(TabCourses.COL_NAME_STUDENT, studentId);
        return db.insert(TabCourses.TAB_NAME_COURSES, null, cv);
    }

    // if courses == null returns all rows
    // otherwise filters by course and mark
    public Cursor queryStudents(String course, int mark, String limit)
            throws SQLiteException {
        SQLiteDatabase db = studentsDBHelper.getWritableDatabase();
        if(TextUtils.isEmpty(course)) {
            return db.query(TabStudents.TAB_NAME_STUDENTS, null, null, null, null, null, null, limit);
        } else {
            // can't just "select *", because both table have "_id" col, they two could be confused
            return db.rawQuery("SELECT "
                    + TabStudents.TAB_NAME_STUDENTS + "." + TabStudents.COL_NAME_ID
                    + ", " + TabStudents.TAB_NAME_STUDENTS + "." + TabStudents.COL_NAME_FIRST_NAME
                    + ", " + TabStudents.TAB_NAME_STUDENTS + "." + TabStudents.COL_NAME_LAST_NAME
                    + ", " + TabStudents.TAB_NAME_STUDENTS + "." + TabStudents.COL_NAME_BIRTHDAY
                    + ", " + TabCourses.TAB_NAME_COURSES + "." + TabCourses.COL_NAME_NAME
                    + ", " + TabCourses.TAB_NAME_COURSES + "." + TabCourses.COL_NAME_MARK
                    + ", " + TabCourses.TAB_NAME_COURSES + "." + TabCourses.COL_NAME_STUDENT
                    + " FROM " + TabStudents.TAB_NAME_STUDENTS
                    + " INNER JOIN " + TabCourses.TAB_NAME_COURSES + " on "
                    + TabStudents.TAB_NAME_STUDENTS + "." + TabStudents.COL_NAME_ID + " = "
                    + TabCourses.TAB_NAME_COURSES + "." + TabCourses.COL_NAME_STUDENT
                    + " WHERE " + TabCourses.COL_NAME_NAME + " = '" + course
                    + "' AND " + TabCourses.COL_NAME_MARK + " = " + mark
                    + " LIMIT " + limit + ";",
                    null
            );
        }
    }

    // returns courses for student
    public Cursor queryCourses(String studentId) throws SQLiteException {
        SQLiteDatabase db = studentsDBHelper.getWritableDatabase();
        return db.query(TabCourses.TAB_NAME_COURSES, null,
                //TabCourses.COL_NAME_STUDENT + " = " + studentId, null, // error
                TabCourses.COL_NAME_STUDENT + " = ? ", new String[] {studentId},
                null, null, null);
    }

    //------------------------------
    private static class StudentsDBHelper extends SQLiteOpenHelper {
        public StudentsDBHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
            super(context, name, factory, version);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(TabStudents.QUERY_CREATE_TAB_STUDENTS);
            db.execSQL(TabCourses.QUERY_CREATE_TAB_COURSES);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // no upgrade supported yet
        }
    }
}
