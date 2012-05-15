package com.balatong.zip.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.balatong.logger.Logger;
import com.balatong.zip.R;
import com.balatong.zip.helper.StatsUtil;
import com.balatong.zip.viewer.ViewerActivity;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;

public class CrcValidator extends AsyncTask<Void, Object, List<String>> {

	private Logger logger = Logger.getLogger(CrcValidator.class.getName());

	final public static String NEW_ENTRY = "NEW_ENTRY";
	final public static String OLD_ENTRY = "OLD_ENTRY";
	final public static String ENTRY_SIZE = "ENTRY_SIZE";
	final public static String BYTES_READ = "BYTES_READ";
	final public static String TOTAL_FILES = "TOTAL_FILES";
	final public static String TOTAL_SIZE = "TOTAL_SIZE";

	private Context context;
	private Map<String, Object> zipEntries;
	private File file;

	private int totalCrcPass = 0;
	private int totalCrcFail = 0;

	public CrcValidator(File file, Map<String, Object> zipEntries, Context context) {
		this.file = file;
		this.zipEntries = zipEntries;
		this.context = context;
	}
	
	@Override
	protected List<String> doInBackground(Void... args) {
		long current = System.currentTimeMillis();
		List<String> failedCrcEntries = crcCheck();
		logger.debug("Elapsed CRC check: " + (System.currentTimeMillis() - current) / 1000 + " secs.");
		return failedCrcEntries;
	}
	
	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		logger.debug("Retrieving stats.");
		StatsUtil statsUtil = StatsUtil.getInstance();
		statsUtil.retrieveToBeExtractedStats(zipEntries);
		LocalBroadcastManager.getInstance(context).sendBroadcast(wrapIntent(
				ViewerActivity.VA_START_PROCESS_CONTENT, 
				ViewerActivity.STATUS_TEXT, context.getResources().getString(R.string.validating_files),
				ViewerActivity.TITLE_TEXT, context.getResources().getString(R.string.validating_files),
				TOTAL_FILES, statsUtil.getNumToBeExtracted(),
				TOTAL_SIZE, statsUtil.getTotalSizeToBeExtracted()
		));			
	}
	
	@Override
	protected void onPostExecute(List<String> result) {
		super.onPostExecute(result);
		LocalBroadcastManager.getInstance(context).sendBroadcast(wrapIntent(
				ViewerActivity.VA_END_PROCESS_CONTENT, 
				ViewerActivity.STATUS_TEXT, context.getResources().getString(R.string.validated_file, result.size())
		));			
	}
	
	@Override
	protected void onProgressUpdate(Object... values) {
		super.onProgressUpdate(values);

		if (NEW_ENTRY.equals(values[0])) {
			LocalBroadcastManager.getInstance(context).sendBroadcast(wrapIntent(
					ViewerActivity.VA_SHOW_NEW_PROGRESS_INFO, 
					ViewerActivity.STATUS_TEXT, context.getResources().getString(R.string.calculating_crc, values[1]),
					ENTRY_SIZE, values[2]
			));			
		}
		else {
			LocalBroadcastManager.getInstance(context).sendBroadcast(wrapIntent(
					ViewerActivity.VA_SHOW_UPDATE_PROGRESS_INFO, 
					BYTES_READ, values[1]
			));			
		}
	}

	private List<String> crcCheck() {
		ZipFile zipFile = null;;
		try {
			logger.info("Opening up " + file.getName() + ".");
			zipFile = new ZipFile(file);
			return verifyCrc(zipEntries, zipFile);			
		}
		catch (Exception e) {
			logger.error("Unable to verify files." + e.getMessage(), e);
			return new ArrayList<String>();
		}
		finally {
			logger.info("Closing " + zipFile.getName() + ".");
			try {
				if (zipFile != null)
					zipFile.close();
			}
			catch (IOException e) {
			}
		}

	}
	
	private List<String> verifyCrc(Map<String, Object> entries, ZipFile zipFile) throws IOException {
		List<String> failedCrc = new ArrayList<String>();
		for (Map.Entry<String, Object> entry : entries.entrySet()) {
			if (entry.getValue() instanceof ZipEntry) {

				publishProgress(NEW_ENTRY, entry.getKey(), (int)((ZipEntry)entry.getValue()).getSize());
				ZipEntry zipEntry = (ZipEntry)entry.getValue();
				long storedCrc = zipEntry.getCrc();
				InputStream is = zipFile.getInputStream(zipEntry);
				long computedCrc = computeCrc(is);
				is.close();
				
				if(computedCrc != storedCrc) {
					totalCrcFail++;
					failedCrc.add(zipEntry.getName());
				}
				else
					totalCrcPass++;
			}
			else {
				if (!"..".equals(entry.getKey())) {
					failedCrc.addAll(verifyCrc((Map<String, Object>)entry.getValue(), zipFile));
				}
			}
		}
		return failedCrc;
	}

	private long computeCrc(InputStream is) throws IOException {
		CRC32 crc32 = new CRC32();
		int count = 0;
		byte buffer[] = new byte[24 * 1024];
		while ((count = is.read(buffer)) > 0) {
			crc32.update(buffer, 0, count);
			publishProgress(OLD_ENTRY, count);
		}
		return crc32.getValue();
	}

	private Intent wrapIntent(String action, Object... extras) {
		Intent intent = new Intent(context, ViewerActivity.class);
		intent.setAction(action);
		for (int i=0; i<(extras.length/2); i++) {
			String key = (String)extras[(2*i)];
			Object value = extras[(2*i)+1];
			if (value instanceof String) 
				intent.putExtra(key, (String)value);
			else if (value instanceof Integer)
				intent.putExtra(key, (Integer)value);
			else if (value instanceof Long)
				intent.putExtra(key, (Long)value);
			else if (value instanceof Boolean)
				intent.putExtra(key, (Boolean)value);
		}
		return intent;
	}

}