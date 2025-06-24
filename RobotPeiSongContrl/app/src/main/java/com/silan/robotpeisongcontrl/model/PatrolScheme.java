package com.silan.robotpeisongcontrl.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PatrolScheme {
    private int schemeId;
    private List<PatrolPoint> points = new ArrayList<>();
    private Date createdDate;

    public PatrolScheme(int schemeId, List<PatrolPoint> points) {
        this.schemeId = schemeId;
        this.points = new ArrayList<>(points);
        this.createdDate = new Date();
    }

    public int getSchemeId() {
        return schemeId;
    }

    public List<PatrolPoint> getPoints() {
        return points;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void addPoint(PatrolPoint point) {
        points.add(point);
    }

    public void removePoint(int index) {
        if (index >= 0 && index < points.size()) {
            points.remove(index);
        }
    }

    public void clearPoints() {
        points.clear();
    }
}