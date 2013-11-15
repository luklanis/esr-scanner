package ch.luklanis.esscan.history;

import android.os.AsyncTask;
import android.text.TextUtils;

public final class HistoryExportUpdateAsyncTask extends AsyncTask<HistoryItem, Void, Void> {

    private final HistoryManager historyManager;
    private final String fileName;

    public HistoryExportUpdateAsyncTask(HistoryManager historyManager, String fileName) {
        this.historyManager = historyManager;
        this.fileName = fileName;
    }

    @Override
    protected Void doInBackground(HistoryItem... params) {
        if (params.length > 0) {
            for (HistoryItem item : params) {
                if (!TextUtils.isEmpty(item.getDTAFilename())) {
                    historyManager.updateHistoryItemFileName(item.getItemId(), fileName);
                }
            }
        }

        return null;
    }
}
