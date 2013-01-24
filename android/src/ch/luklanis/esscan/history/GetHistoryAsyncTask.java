package ch.luklanis.esscan.history;

import java.util.ArrayList;
import java.util.List;

import android.os.AsyncTask;

public class GetHistoryAsyncTask extends AsyncTask<Void, Void, Boolean> {
	
	private HistoryFragment historyFragment;
	private HistoryManager historyManager;
	private HistoryItemAdapter adapter;

	public GetHistoryAsyncTask(HistoryFragment historyFragment, HistoryManager historyManager) {
		this.historyFragment = historyFragment;
		this.historyManager = historyManager;
		
		historyFragment.setListShown(false);
		historyFragment.setListAdapter(null);

		adapter = new HistoryItemAdapter(historyFragment.getActivity());
	}

	@Override
	protected Boolean doInBackground(Void... params) {

		List<HistoryItem> items = historyManager.buildAllHistoryItems();
		
		for (HistoryItem item : items) {
			adapter.add(item);
		}
		
		return true;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		if (result) {
			historyFragment.setHistoryItemAdapter(adapter);
		}
	}
}