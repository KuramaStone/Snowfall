package me.brook.selection.tools;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import me.brook.neat.species.NeatInnovation;

public class Profiler {

	private static final DecimalFormat df = new DecimalFormat("##.000000000");
	private static final DecimalFormat df2 = new DecimalFormat("##.00000");

	public static ConcurrentHashMap<String, ProfileData> data = new ConcurrentHashMap<>();
	
	public static void resetProfileData() {
		data = new ConcurrentHashMap<>();
	}

	public static String writeProfileData() {
		StringBuilder sb = new StringBuilder();
		long sum = 0;
		for(Entry<String, ProfileData> key : data.entrySet()) {
			sum += key.getValue().getAverageTime();
		}

		for(Entry<String, ProfileData> key : data.entrySet()) {
			sb.append(String.format("%s: %s recordings, %s total, %s average, %s percent\n", key.getKey(), key.getValue().getRecordings(),
					key.getValue().getTotalTime() / 1e9, key.getValue().getAverageTime() / 1e9,
							df2.format((double) key.getValue().getAverageTime() / sum * 100)));
		}

		return sb.toString();
	}

	public static void startRecord(String id) {
		ProfileData data = getOrMakeProfile(id);
		data.startRecording();
	}

	public static void stopRecord(String id) {
		ProfileData data = getOrMakeProfile(id);
		data.stopRecording();
	}

	public static void addRecord(String id, long nanoseconds) {
		ProfileData data = getOrMakeProfile(id);
		data.recordings++;
		data.totalTime += nanoseconds;
	}

	public static ProfileData getProfile(String id) {
		return data.get(id);
	}

	private static ProfileData getOrMakeProfile(String id) {
		ProfileData prof = data.get(id);

		if(prof == null) {
			prof = new ProfileData();
			data.put(id, prof);
		}

		return prof;
	}

	public static class ProfileData {
		private int recordings; // total times recorded
		private long totalTime; // total time taken to run this profile
		private boolean recording; // is it recording now
		private long recordingStart;

		public ProfileData() {
		}

		public void startRecording() {
			recording = true;
			recordingStart = System.nanoTime();
		}

		public void stopRecording() {
			long time = System.nanoTime() - recordingStart;
			recording = false;
			totalTime += time;
			recordings++;
		}

		public boolean isRecording() {
			return recording;
		}

		public int getRecordings() {
			return recordings;
		}

		public long getTotalTime() {
			return totalTime;
		}

		public long getAverageTime() {
			return totalTime / Math.max(1, recordings);
		}
	}

}
