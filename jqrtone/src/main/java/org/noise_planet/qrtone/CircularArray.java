package org.noise_planet.qrtone;

import java.util.AbstractList;

/**
 * Keep fixed sized array of element, without moving elements at each insertion
 */
public class CircularArray extends AbstractList<Float> {
    float[] values;
    int cursor=0;
    int inserted = 0;

    public CircularArray(int size) {
        values = new float[size];
    }

    @Override
    public Float get(int index) {
        int cicularIndex = cursor - inserted + index;
        if(cicularIndex < 0) {
            cicularIndex += values.length;
        }
        return values[cicularIndex];
    }

    @Override
    public boolean add(Float value) {
        values[cursor] = value;
        cursor += 1;
        if(cursor == values.length) {
            cursor = 0;
        }
        inserted = Math.min(values.length, inserted + 1);
        return true;
    }

    @Override
    public int size() {
        return inserted;
    }
}