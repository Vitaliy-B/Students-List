package bv.dev.ddapp.studentslist;

import android.content.DialogInterface;
import android.database.Cursor;

// thick interface for dialog fragments in main activity
public interface IDialogFragmentCallback {
    Cursor getCursor();
    DialogInterface.OnClickListener getDIOCL();
    void onDone();
}
