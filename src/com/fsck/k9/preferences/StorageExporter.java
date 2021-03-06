package com.fsck.k9.preferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.util.Log;

import com.fsck.k9.K9;
import com.fsck.k9.R;
import com.fsck.k9.activity.AsyncUIProcessor;
import com.fsck.k9.activity.ExportListener;
import com.fsck.k9.activity.PasswordEntryDialog;

public class StorageExporter {
    private static void exportPreferences(Activity activity, String version, String uuid, String fileName, OutputStream os, String encryptionKey, final ExportListener listener)  {
        try {
            IStorageExporter storageExporter = StorageVersioning.createExporter(version);
            if (storageExporter == null) {
                throw new StorageImportExportException(activity.getString(R.string.settings_unknown_version, version), null);
            }
            if (storageExporter.needsKey() && encryptionKey == null) {
                gatherPassword(activity, storageExporter, uuid, fileName, os, listener);
            } else {
                finishExport(activity, storageExporter, uuid, fileName, os, encryptionKey, listener);
            }
        }

        catch (Exception e) {
            if (listener != null) {
                listener.failure(e.getLocalizedMessage(), e);
            }
        }
    }

    public static void exportPreferences(Activity activity, String version, String uuid, String fileName, String encryptionKey, final ExportListener listener) throws StorageImportExportException {
        exportPreferences(activity, version, uuid, fileName, null, encryptionKey, listener);
    }

    public static void exportPrefererences(Activity activity, String version, String uuid, OutputStream os, String encryptionKey, final ExportListener listener) throws StorageImportExportException {
        exportPreferences(activity, version, uuid, null, os, encryptionKey, listener);
    }

    private static void gatherPassword(final Activity activity, final IStorageExporter storageExporter, final String uuid, final String fileName, final OutputStream os, final ExportListener listener) {
        activity.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                PasswordEntryDialog dialog = new PasswordEntryDialog(activity, activity.getString(R.string.settings_encryption_password_prompt),
                new PasswordEntryDialog.PasswordEntryListener() {
                    public void passwordChosen(final String chosenPassword) {

                        AsyncUIProcessor.getInstance(activity.getApplication()).execute(new Runnable() {

                            @Override
                            public void run() {
                                try {
                                    finishExport(activity, storageExporter, uuid, fileName, os, chosenPassword, listener);
                                } catch (Exception e) {
                                    Log.w(K9.LOG_TAG, "Exception while finishing export", e);
                                    if (listener != null) {
                                        listener.failure(e.getLocalizedMessage(), e);
                                    }
                                }
                            }
                        });

                    }

                    public void cancel() {
                        if (listener != null) {
                            listener.canceled();
                        }
                    }
                });
                dialog.show();
            }
        });
    }


    private static void finishExport(Activity activity, IStorageExporter storageExporter, String uuid, String fileName, OutputStream os, String encryptionKey, ExportListener listener) throws StorageImportExportException {
        boolean needToClose = false;
        if (listener != null) {
            listener.started();
        }
        try {
            // This needs to be after the password prompt.  If the user cancels the password, we do not want
            // to create the file needlessly
            if (os == null && fileName != null) {
                needToClose = true;
                File outFile = new File(fileName);
                os = new FileOutputStream(outFile);
            }
            if (os != null) {
                storageExporter.exportPreferences(activity, uuid, os, encryptionKey);
                if (listener != null) {
                    if (fileName != null) {
                        listener.success(fileName);
                    } else {
                        listener.success();
                    }
                }
            } else {
                throw new StorageImportExportException("Internal error; no fileName or OutputStream", null);
            }
        } catch (Exception e) {
            throw new StorageImportExportException(e.getLocalizedMessage(), e);
        } finally {
            if (needToClose && os != null) {
                try {
                    os.close();
                } catch (Exception e) {
                    Log.w(K9.LOG_TAG, "Unable to close OutputStream", e);
                }
            }
        }

    }

}
