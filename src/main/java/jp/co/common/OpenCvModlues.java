package jp.co.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.*;


/**
 * Created by sarichi on 2015/12/02.
 */
public class OpenCvModlues {
    Logger logger = LogManager.getLogger(OpenCvModlues.class);

    //Maven Project として インストール
    //mvn install:install-file -Dfile=opencv-300.jar -DgroupId=usr.local.Cellar.opencv3.3.0.0.share.OpenCV.java -DartifactId=opencv-300 -Dversion=1.0 -Dpackaging=jar -DgeneratePom=true
    static {
        System.load("/usr/local/Cellar/opencv3/3.0.0/share/OpenCV/java/libopencv_java300.dylib");
    }

    public void grayscale(String src_path) {
        Mat im = Imgcodecs.imread(src_path);    // 入力画像の取得
        Imgproc.cvtColor(im, im, Imgproc.COLOR_RGB2GRAY);       // 画像のグレースケール変換
        Imgcodecs.imwrite(src_path, im);            // 画像データをJPG形式で保存
    }

    public void resize(String path, double size) {
        Mat src = Imgcodecs.imread(path);    // 入力画像の取得
        Mat resize_src = new Mat();
        Size sz = src.size();
        Imgproc.resize(src, resize_src, new Size(sz.width * size, sz.height * size));
        Imgcodecs.imwrite(path, resize_src);            // 出力画像の保存
    }


    public boolean diff(String src_path, String templ_path, double thresholed) {
        //対象画像とテンプレート画像を読み込み
        boolean result = false;
        Mat src = Imgcodecs.imread(src_path, Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);
        Mat templ = Imgcodecs.imread(templ_path, Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);
        return diff_core(src, templ, thresholed);
    }

    /**
     * テンプレートマッチングを実行する
     *
     * @return
     */
    public double[] template_matching(String src_path, String templ_path) {
        Mat src = Imgcodecs.imread(src_path);                    // 入力画像の取得
        Mat templ = Imgcodecs.imread(templ_path);                    // テンプレート画像の取得
        return template_matching_core(src, templ);
    }

    /**
     * テンプレートマッチングを実行する
     *
     * @return
     */
    public double[] template_matching(byte[] src,int col,int row ,String templ_path) {
        Mat mat = new Mat( 768,1280, CvType.CV_8UC(3));
        mat.put(0, 0, src);// 入力画像の取得
        Mat templ = Imgcodecs.imread(templ_path);                    // テンプレート画像の取得
        return template_matching_core(mat, templ);
    }

    /**
     * テンプレートマッチングを実行
     *
     * @param src_path
     * @param templ_path
     * @param thresholed
     * @return
     */
    public double[][] any_template_matching(String src_path, String templ_path, double thresholed) {
        //対象画像とテンプレート画像を読み込み
        Mat src = Imgcodecs.imread(src_path);                    // 入力画像の取得
        Mat templ = Imgcodecs.imread(templ_path);                    // テンプレート画像の取得
        return template_matching_core(src, templ, thresholed);
    }


    /**
     * テンプレートマッチングを実行
     *
     * @param src_path
     * @param templ_path
     * @param thresholed
     * @return
     */
    public double[][] template_matching(String src_path, String templ_path, double thresholed) {
        //対象画像とテンプレート画像を読み込み
        Mat src = Imgcodecs.imread(src_path);                    // 入力画像の取得
        Mat templ = Imgcodecs.imread(templ_path);                    // テンプレート画像の取得
        return template_matching_core(src, templ, thresholed);
    }


    /**
     * テンプレートマッチングを実行する
     *
     * @return
     */
    private double[] template_matching_core(Mat src, Mat templ) {
        Mat result = new Mat();

        Imgproc.matchTemplate(src, templ, result, Imgproc.TM_CCOEFF_NORMED);
        //最高一致いた x、yを取得
        Core.MinMaxLocResult maxr = Core.minMaxLoc(result);
        Point maxp = maxr.maxLoc;

        //デバッグ用に画像出力
        Point pt2 = new Point(maxp.x + templ.width(), maxp.y + templ.height());
        Mat dst = src.clone();
        Imgproc.rectangle(dst, maxp, pt2, new Scalar(255, 0, 0), 2);
        Imgcodecs.imwrite("./work/template_matching.png", dst);

        //タップするべき中央のoffset値を算出
        double x = maxp.x + templ.width() / 2;
        double y = maxp.y + templ.height() / 2;
        double certainty = maxr.maxVal;
        logger.debug(String.format("template_matching x:%.2f y:%.2f certainty:%.2f", x, y, certainty));
        return new double[]{x, y, certainty};
    }

    private double[][] template_matching_core(Mat src, Mat templ, double thresholed) {
        //比較結果を格納するMatを生成
        Mat result = new Mat(src.rows() - templ.rows() + 1, src.cols() - templ.cols() + 1, CvType.CV_32FC1);
        //テンプレートマッチ実行（TM_CCOEFF_NORMED：相関係数＋正規化）
        Imgproc.matchTemplate(src, templ, result, Imgproc.TM_CCOEFF_NORMED);
        //結果から相関係数がしきい値以下を削除（０にする）
        Imgproc.threshold(result, result, thresholed, 1.0, Imgproc.THRESH_TOZERO);

        //返却用リスト作成
        List<double[]> any_offset = new ArrayList<double[]>();
        double add_x = templ.width() / 2;
        double add_y = templ.height() / 2;

        Mat dst = src.clone();
        // 取得した値で 0 以外の要素取得する
        for (int i = 0; i < result.rows(); i++) {
            for (int j = 0; j < result.cols(); j++) {
                if (result.get(i, j)[0] > 0) {
                    any_offset.add(new double[]{j + add_x, i + add_y, result.get(i, j)[0]});
                    //デバッグ用に画像出力
                    Imgproc.rectangle(dst, new Point(j, i), new Point(j + templ.cols(), i + templ.rows()), new Scalar(0, 0, 255));
                }
            }
        }
        Imgcodecs.imwrite("./work/any_template_matching.png", dst);

        //ソート
        Comparator<double[]> sumComparator = Comparator.comparingDouble(d -> d[0] + d[1]);
        Comparator<double[]> xComparator = Comparator.comparingDouble(d -> d[0]);
        Comparator<double[]> yComparator = Comparator.comparingDouble(d -> d[1]);
        Comparator<double[]> comparator = sumComparator.thenComparing(xComparator.thenComparing(yComparator));
        Collections.sort(any_offset, comparator);

        double ptx = 0;
        double pty = 0;
        //近似値すぎるポイントは破棄する
        Iterator<double[]> iterator_1 = any_offset.iterator();


        while (iterator_1.hasNext()) {
            double[] p = iterator_1.next();
            if (Math.abs(p[0] - ptx) > 40 || Math.abs(p[1] - pty) > 40) {
                ptx = p[0];
                pty = p[1];
            } else {
                iterator_1.remove();
            }
        }
        //配列へ詰め替え
        double[][] ret = new double[any_offset.size()][3];
        for (int i = 0; i < any_offset.size(); i++) {
            ret[i] = any_offset.get(i);
        }
        return ret;
    }


    private boolean diff_core(Mat src, Mat templ, double thresholed) {
        //対象画像とテンプレート画像を読み込み
        boolean result = false;
        Mat dst = new Mat();
        Core.absdiff(src, templ, dst);
        int diff = Core.countNonZero(dst);
        Size s = src.size();
        int src_size = (int) s.height * (int) s.width;

        if (diff / src_size < thresholed) {
            result = true;
        }
        return result;
    }


    /**
     * BufferedImageをMatファイルへ変換する
     *
     * @param bufferedImage
     * @return
     */
    private Mat matify(BufferedImage bufferedImage) {
        // Convert bufferedimage to byte array
        byte[] pixels = ((DataBufferByte) bufferedImage.getRaster().getDataBuffer())
                .getData();
        // Create a Matrix the same size of image
        Mat mat = new Mat(bufferedImage.getHeight(), bufferedImage.getWidth(), CvType.CV_8UC3);
        // Fill Matrix with image values
        mat.put(0, 0, pixels);
        return mat;
    }

    private BufferedImage BufferedImagefy(Mat mat) {
        BufferedImage bufferedImage;
        int height = mat.height();
        int width = mat.width();
        byte[] data = new byte[height * width * (int) mat.elemSize()];
        int type;
        mat.get(0, 0, data);
        if (mat.channels() == 1)
            type = BufferedImage.TYPE_BYTE_GRAY;
        else
            type = BufferedImage.TYPE_3BYTE_BGR;

        bufferedImage = new BufferedImage(width, height, type);

        bufferedImage.getRaster().setDataElements(0, 0, width, height, data);
        return bufferedImage;
    }
}
