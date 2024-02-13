package com.openpositioning.PositionMe.fragments;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import java.util.HashMap;
import java.util.Map;

public class Building {
    private String name;
    private LatLngBounds bounds;
    private Map<Integer, Integer> floorMaps; // Maps floor number to drawable resource ID

    public Building(String name, LatLngBounds bounds) {
        this.name = name;
        this.bounds = bounds;
        this.floorMaps = new HashMap<>();
    }

    public void addFloorMap(int floor, int drawableResourceId) {
        floorMaps.put(floor, drawableResourceId);
    }

    public String getName() {
        return name;
    }

    public LatLngBounds getBounds() {
        return bounds;
    }

    public Integer getFloorMapResourceId(int floor) {
        return floorMaps.get(floor);
    }

    public boolean contains(LatLng position) {
        return bounds.contains(position);
    }
}
