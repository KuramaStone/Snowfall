package me.brook.selection.tools;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import me.brook.neat.species.NeatInnovation;

public class Tracker<T> {

	private static final DecimalFormat df = new DecimalFormat("##.000000000");
	private static final DecimalFormat df2 = new DecimalFormat("##.00000");

	public ConcurrentHashMap<String, TrackerData<T>> data = new ConcurrentHashMap<>();

	public void resetProfileData() {
		data = new ConcurrentHashMap<>();
	}

	public void addData(String id, T value) {
		TrackerData<T> td = getOrMakeProfile(id);
		td.list.add(value);
	}

	public void clear() {
		data.clear();
	}

	public TrackerData<T> getTrackerData(String id) {
		return data.get(id);
	}

	private TrackerData<T> getOrMakeProfile(String id) {
		TrackerData<T> prof = data.get(id);

		if(prof == null) {
			prof = new TrackerData<T>();
			this.data.put(id, prof);
		}

		return prof;
	}

	public class TrackerData<T> {
		private List<T> list;

		public TrackerData() {
			list = new ArrayList<>();
		}

		public List<T> getData() {
			return list;
		}
	}

}
