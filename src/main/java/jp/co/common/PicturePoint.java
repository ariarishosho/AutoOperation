package jp.co.common;

/**
 * Created by sarichi on 2016/02/11.
 */
public class PicturePoint {
    private int x;
    private int y;
    private double certainty;

    public void setX(double x) {
        this.x = (int) x;
    }

    public void setY(double y) {
        this.y = (int) y;
    }

    public void setCertainty(double certainty) {
        this.certainty = certainty;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public double getCertainty() {
        return certainty;
    }
}
