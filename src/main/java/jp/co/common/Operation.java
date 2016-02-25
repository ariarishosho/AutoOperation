package jp.co.common;

import io.appium.java_client.MobileElement;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.AndroidKeyCode;
import io.appium.java_client.remote.MobileCapabilityType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class Operation<T> {
    //デバイス
    AndroidDriver driver;
    OpenCvModlues cv;

    private String device_id;
    private String device_id_e;
    private String work_image;
    private String work_preimage;
    private String result_path;
    private String ocr_path;
    private String tmp_path;
    private String tmp_rare_path;

    Logger logger = LogManager.getLogger(Operation.class);

    //システム変数
    private double certainty_base = 0.75;
    private int retry_base = 25;
    private String preParts = "";
    private String[] prePartsInterrupt = {};
    private double preCertainty = 0d;
    private int port;
    private int bootstrap_port;


    public Operation(String device_id, int port, int bootstrap_port) {

        this.device_id = device_id;
        this.device_id_e = device_id.replace(".", "").replace(":", "");

        this.work_image = "./work/" + device_id_e + "_current.png";
        this.work_preimage = "./work/" + device_id_e + "_pre_current.png";
        this.result_path = "./result/" + device_id_e + "_result.txt";
        this.ocr_path = "./work/" + device_id_e + "_ocr.png";
        this.tmp_path = "./work/" + device_id_e + "_tmp";
        this.tmp_rare_path = "./work/" + device_id_e + "_rare_tmp";
        this.port = port;
        this.bootstrap_port = bootstrap_port;

        this.cv = new OpenCvModlues();

    }


    /**
     * ログ処理
     *
     * @param massage
     */
    private void debugLog(String massage) {
        logger.debug("{}:{}", this.device_id, massage);
    }

    public String adbCommandExec(String command) {
        String adb_command = String.format("adb -s %s shell %s", device_id, command);
        return commadExcec(adb_command);
    }

    /**
     * コマンドを実行して
     *
     * @param cmd
     * @return
     */
    public String commadExcec(String cmd) {
        String ret = "";
        ProcessBuilder pb = new ProcessBuilder(cmd.split(" "));
        try {
            Process process = pb.start();
            InputStream in = process.getInputStream();

            InputStreamReader isr = new InputStreamReader(in, "UTF-8");
            BufferedReader reader = new BufferedReader(isr);
            StringBuilder builder = new StringBuilder();
            int c;
            while ((c = reader.read()) != -1) {
                builder.append((char) c);
            }
            process.waitFor();
            ret = builder.toString();

        } catch (IOException | InterruptedException e) {
            logger.error("エラー:", e);
        }

        return ret;
    }


    /**
     * コネクションを生成して、アクティビティを起動させる
     *
     * @param package_name
     * @param activity_name
     * @param clazz
     */
    @SuppressWarnings("all")
    public void initDevice(String package_name, String activity_name) {
        try {
            DesiredCapabilities capabilities = new DesiredCapabilities();
            capabilities.setCapability(MobileCapabilityType.APPIUM_VERSION, "1.0");
            capabilities.setCapability(MobileCapabilityType.PLATFORM_VERSION, "4.3");
            capabilities.setCapability(MobileCapabilityType.TAKES_SCREENSHOT, "true");
            capabilities.setCapability(MobileCapabilityType.DEVICE_NAME, this.device_id);

            capabilities.setCapability(MobileCapabilityType.APP_PACKAGE, package_name);
            capabilities.setCapability(MobileCapabilityType.APP_ACTIVITY,
                    activity_name);
            driver = new AndroidDriver<MobileElement>(new URL(String.format("http://0.0.0.0:%d/wd/hub", port)),
                    capabilities);
            driver.manage().timeouts().implicitlyWait(180, TimeUnit.SECONDS);

        } catch (SecurityException e) {
            throw new RuntimeException(e);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

    }


    public void close_task() {
        if (this.driver != null)
            this.driver.quit();
    }

    private void sleep(long time) {
        try {

            Thread.sleep(time / 4);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    /**
     * AndroidKeyCodeのキーコード
     *
     * @param key
     */
    public void press(int key) {
        driver.pressKeyCode(key);
    }

    public void tap(int x, int y) {
        driver.tap(1, x, y, 200);
    }

    public void double_tap(int x, int y) {
        driver.tap(1, x, y, 200);
        driver.tap(1, x, y, 200);
    }

    public void long_tap(int x, int y) {
        driver.tap(1, x, y, 1000);
    }

    public void sendkey(String message, String xpath) {
        debugLog("sendkey=" + message);
        WebElement element = driver.findElement(By.xpath(xpath));
        element.sendKeys(message);
        driver.pressKeyCode(AndroidKeyCode.ENTER);
        sleep(1500L);
    }

    public void wait_screen() {
        debugLog("wait_screen");
        sleep(1000l);
    }


    public void closeApp() {
        driver.closeApp();
    }

    /**
     * 指定した区画の画像をOCRにかける
     *
     * @param offset_x
     * @param offset_y
     * @param width
     * @param height
     * @return
     */
    public String get_ocr_data(int offset_x, int offset_y, int width, int height) {
        File f = null;
        FileReader filereader = null;
        BufferedImage readImage = null;
        String ocr_data = "";
        try {
            File screenshot = driver.getScreenshotAs(OutputType.FILE);
            readImage = ImageIO.read(screenshot);
            readImage = readImage.getSubimage(offset_x, offset_y, width, height);
            ImageIO.write(readImage, "png", new File(ocr_path));

            // 色を落とす
            // gray_cmd = "convert -type GrayScale " + ocr_path + " " + ocr_path
//            String gray_cmd = "convert -threshold 48000 " + ocr_path + " " + ocr_path
//            this.commadExcec(gray_cmd);
            cv.grayscale(ocr_path);

            // OCR 読み取り
            String tesseract_cmd = "tesseract " + ocr_path + " " + tmp_path + " -l jpn -psm 7 tesseract_star.conf";
            // tesseract_cmd = "tesseract "+ocr_path + " " + tmp_path + " -l jpn -psm 7 "
            commadExcec(tesseract_cmd);
            f = new File(tmp_path + ".txt");
            filereader = new FileReader(f);
            StringBuilder builder = new StringBuilder();
            int ch;
            while ((ch = filereader.read()) != -1) {
                builder.append((char) ch);
            }
            ocr_data = builder.toString();
            filereader.close();
            debugLog("ocr解析結果:" + ocr_data);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            readImage.flush();
            readImage = null;

            if (filereader != null) {
                filereader = null;
            }
        }
        return ocr_data;
    }


    /**
     * ガチャの結果キャプチャ格納
     *
     * @return
     */
    public String saveResult() {
        // 星の数を判定
        // ocr_data = get_ocr_data( 138, 62, 341, 52) 名前取得
        String ocr_data = get_ocr_data(412, 48, 148, 30);
        // ocr_data = get_ocr_data( 138,62 , 458, 42) 星六名前
        ocr_data = ocr_data.replace("\n", "").replace(" ", "");
        debugLog(" リセマラ結果:[" + ocr_data + "]:");
        // 現在時間を取得
        Date tdatetime = new Date();
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyyMMddHHmmss");
        String tstr = sdf1.format(tdatetime);
        if (ocr_data.contains("*****")) {
            debugLog(" リセマラ当たり！！！！");
            // 格納フォルダを作る
            String cmd = "mkdir ./result/" + tstr;
            this.commadExcec(cmd);

            // 保存するファイル名
            String screenshot_path = "./result/" + tstr + "/screenshot.png";
            BufferedImage readImage = null;
            try {
                File screenshot = driver.getScreenshotAs(OutputType.FILE);
                readImage = ImageIO.read(screenshot);
                ImageIO.write(readImage, "png", new File(screenshot_path));
            } catch (IOException e) {
                e.printStackTrace();

            } finally {
                readImage.flush();
                readImage = null;
            }

            // 容量削減のため縮小
            cv.resize(screenshot_path, 0.3);
            // データ取り出し
            cmd = "adb -s " + device_id + " pull /data/data/jp.co.mixi.monsterstrike/data10.bin " + "./result/" + tstr + "/data10.bin";
            this.commadExcec(cmd);
            cmd = "adb -s " + device_id + " pull /data/data/jp.co.mixi.monsterstrike/data13.bin " + "./result/" + tstr + "/data13.bin";
            this.commadExcec(cmd);
        }
        try {
            File f = new File(result_path);
            FileWriter filewriter = new FileWriter(f, true);

            filewriter.write(tstr + "\n");
            filewriter.write(ocr_data + "\n");

            filewriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ocr_data;
    }

    /**
     * テンプレートマッチング
     * 最も一致したオフセットが返却される
     *
     * @param path
     * @return [0]:x [1]:y [2]:certainty
     */
    public double[] call_template_matching(String path) {
        debugLog("対象は" + path);
        long start = System.currentTimeMillis();
        double offset[] = cv.template_matching(work_image, path);
        long end = System.currentTimeMillis();
        debugLog(String.valueOf(end - start) + "ms テンプレートマッチング時間");
        return offset;
    }

    /**
     * テンプレートマッチング
     * 引数のスレッシュホールドで指定した値以上の部分が配列になって返却される
     *
     * @param path
     * @param threshold
     * @return [0]:([0]:x [1]:y [2]:certainty) [1]:([0]:x [1]:y [2]:certainty) ...
     */
    public double[][] call_template_matching(String path, double threshold) {

        double offset[][] = cv.template_matching(work_image, path, threshold);
        return offset;
    }

    /**
     * スクリーンショット
     */


    public void current_screen() {
        long start = System.currentTimeMillis();
//        try {
//            Files.write(Paths.get(work_image), ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));
        adbCommandExec("screencap -p /sdcard/screen.png");
        long end = System.currentTimeMillis();
        debugLog(String.valueOf(end - start) + "ms スクリーンショット screencap時間");
        commadExcec(String.format("adb -s %s pull /sdcard/screen.png %s", this.device_id, work_image));
        end = System.currentTimeMillis();
        debugLog(String.valueOf(end - start) + "ms スクリーンショットadb pull書き出し時間");
        adbCommandExec("rm -rf /sdcard/screen.png");


//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        end = System.currentTimeMillis();
        debugLog(String.valueOf(end - start) + "ms スクリーンショットrm -rf時間");
    }

    /**
     * 画像パスからOffSet値を算出してタップする
     *
     * @param path
     * @return
     */
    public boolean pictureOffSetTap(String path, double exp_certainty) {
        exp_certainty = exp_certainty == 0 ? certainty_base : exp_certainty;
        boolean result = false;
        current_screen();
        if (!path.isEmpty()) {
            double offset[] = call_template_matching(path);
            int x = (int) offset[0];
            int y = (int) offset[1];
            double certainty = offset[2];
            debugLog("pictureOffSetTap 正確性" + String.valueOf(certainty));
            if (certainty >= exp_certainty) {
                tap(x, y);
                result = true;
            }
        }
        return result;
    }


    /**
     * 画像パスから複数にマッチするOffSet値を算出して
     * タップする。その際にmaxで指定した値 まで全てタップする
     *
     * @param path
     * @param max
     * @return
     */
    public boolean pictureMultiTap(String path, int max, double exp_certainty) {
        exp_certainty = exp_certainty == 0 ? certainty_base : exp_certainty;
        boolean result = false;
        current_screen();

        double pintarray[][] = call_template_matching(path, exp_certainty);
        debugLog("pictureMultiTap : " + Arrays.toString(pintarray)
                + " max:" +
                String.valueOf(max)
        );
        if (pintarray.length > 0) {
            result = true;
            Stream.of(pintarray).limit(max).forEach(offset -> {
                int x = (int) offset[0];
                int y = (int) offset[1];
                double certainty = offset[2];
                tap(x, y);
                sleep(500l);
            });
        }
        return result;
    }

    /**
     * 画像パスから複数にマッチするOffSet値を算出して
     * タップする。その際に画面最上部から直線距離が近いものを
     * インデックを指定してタップ。（例：1　→一番画面上で上）
     *
     * @param path
     * @param index
     * @return
     */
    public boolean pictureIndexTap(String path, int index, double exp_certainty) {
        exp_certainty = exp_certainty == 0 ? certainty_base : exp_certainty;
        boolean result = false;
        current_screen();

        double pintarray[][] = call_template_matching(path, exp_certainty);
        debugLog("pictureIndexTap : " + Arrays.toString(pintarray)
                + " index:" +
                String.valueOf(index)
        );
        if (pintarray.length > 0) {
            double offset[] = pintarray[index];

            int x = (int) offset[0];
            int y = (int) offset[1];
            double certainty = offset[2];
            tap(x, y);
            result = true;
        }
        return result;
    }

    /**
     * 特定の画像があるかを判定する
     *
     * @param path
     * @return
     */
    public boolean isExist(String path) {
        boolean result = false;
        current_screen();

        double offset[] = call_template_matching(path);
        int x = (int) offset[0];
        int y = (int) offset[1];
        double certainty = offset[2];
        debugLog("isExistテンプレートマッチング 正確性" + String.valueOf(certainty));
        if (certainty >= certainty_base) {
            result = true;
        }
        return result;
    }

    /**
     * 特定の画像の期待する正確性を指定して判定行う
     *
     * @param path
     * @return
     */
    public boolean isExist(String path, double exp_certainty) {
        boolean result = false;
        current_screen();

        double offset[][] = call_template_matching(path, exp_certainty);
        debugLog("isExist ヒット数 " + String.valueOf(offset.length));

        if (offset.length > 0) {
            result = true;
        }
        return result;
    }

    /**
     * 特定の画像が何個あるかを特定する
     *
     * @param path
     * @return
     */
    public int getExistNum(String path, double exp_certainty) {
        boolean result = false;
        current_screen();

        double offset[][] = call_template_matching(path, exp_certainty);

        debugLog("getExistNumテンプレートマッチング ヒット数 " + String.valueOf(offset.length));

        return offset.length;
    }

    /**
     * 画像の類似度を計算する
     *
     * @return
     */
    public boolean isUpdateState() {
        boolean result = false;
        for (int i = 0; i < retry_base; i++) {
            try {
                Files.deleteIfExists(new File(work_preimage).toPath());
                Files.copy(new File(work_image).toPath(), new File(work_preimage).toPath());
                current_screen();
                if (cv.diff(work_image, work_preimage, 0.95)) {
                    debugLog("スクリーンアップデート完了");
                    result = true;
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            debugLog("opencv側でエラー発生");
        }
        return result;
    }

    /**
     * 割り込み発生する画像の有無を判定し、interruptはクリックする
     *
     * @param interrupt
     * @return
     */
    public boolean execInterrupt(String interrupt[]) {
        boolean result = false;
        debugLog("execInterrupt  " + Arrays.toString(interrupt));
        if (interrupt != null && 0 < interrupt.length && interrupt[0] != null) {
            for (String path : interrupt) {
                debugLog("インターラプタ:" + path);
                if (pictureOffSetTap(path, 0)) {
                    result = true;
                }
            }
        }
        return result;
    }

    /**
     * 割り込み発生する画像の有無を判定
     *
     * @param interrupt
     * @return
     */
    public boolean execInterruptEnd(String interrupt[]) {
        boolean result = false;
        debugLog("execInterrupt  " + Arrays.toString(interrupt));
        if (interrupt != null && 0 < interrupt.length && interrupt[0] != null) {
            for (String path : interrupt) {
                debugLog("インターラプタ:" + path);
                if (isExist(path)) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 特定の要素が出て来るまで待ってタップする
     *
     * @param path
     * @throws Exception
     */
    public void waitForTap(String path) throws Exception {
        for (int i = 0; i < retry_base; i++) {
            debugLog("waitForTap 試行回数" + String.valueOf(i));
            if (pictureOffSetTap(path, 0)) {
                preParts = path;
                preCertainty = certainty_base;
                return;
            } else {
                if (i % 5 == 0) {
                    pictureOffSetTap(preParts, preCertainty);
                    execInterrupt(prePartsInterrupt);
                }
                sleep(1000);
            }
        }
        throw new Exception("waitForTap ERRO");
    }

    /**
     * 特定の要素が出て来るまで待ってタップする
     *
     * @param path
     * @param interrupt
     * @throws Exception
     */
    public void waitForTapInterrupt(String path, String interrupt[]) throws Exception {
        for (int i = 0; i < retry_base; i++) {
            debugLog("waitForTap 試行回数" + String.valueOf(i));
            if (execInterrupt(interrupt)) {
                sleep(2000);
                current_screen();
                continue;
            }
            if (pictureOffSetTap(path, 0)) {
                preParts = path;
                prePartsInterrupt = interrupt;
                preCertainty = certainty_base;
                return;
            } else {
                if (i % 5 == 0) {
                    pictureOffSetTap(preParts, preCertainty);
                    execInterrupt(prePartsInterrupt);
                }
                sleep(1000);
            }
        }
        throw new Exception("waitForTap ERRO");
    }


    /**
     * 特定の要素が出て来るまで待ってタップする
     *
     * @param path
     * @throws Exception
     */
    public void waitForTap(String path, double exp_certainty) throws Exception {
        for (int i = 0; i < retry_base; i++) {
            debugLog("waitForTap 試行回数" + String.valueOf(i));
            if (pictureOffSetTap(path, exp_certainty)) {
                preParts = path;
                preCertainty = exp_certainty;
                return;
            } else {
                if (i % 5 == 0) {
                    pictureOffSetTap(preParts, preCertainty);
                    execInterrupt(prePartsInterrupt);
                }
                sleep(1000);
            }
        }
        throw new Exception("waitForTap ERRO");
    }

    /**
     * 特定の要素が出て来るまで待ってタップする
     *
     * @param path
     * @param interrupt
     * @throws Exception
     */
    public void waitForTapInterrupt(String path, String interrupt[], double exp_certainty) throws Exception {
        for (int i = 0; i < retry_base; i++) {
            debugLog("waitForTap 試行回数" + String.valueOf(i));
            if (execInterrupt(interrupt)) {
                sleep(2000);
                current_screen();
                continue;
            }
            if (pictureOffSetTap(path, exp_certainty)) {
                preParts = path;
                prePartsInterrupt = interrupt;
                preCertainty = exp_certainty;
                return;
            } else {
                if (i % 5 == 0) {
                    pictureOffSetTap(preParts, preCertainty);
                    execInterrupt(prePartsInterrupt);
                }
                sleep(1000);
            }
        }
        throw new Exception("waitForTap ERRO");
    }

    /**
     * 特定の要素が出て来るまで待ってタップする
     *
     * @param path
     * @param interrupt
     * @throws Exception
     */
    public void waitForTapLong(String path, String interrupt[]) throws Exception {
        for (int i = 0; i < retry_base * 4; i++) {
            debugLog("waitForTap 試行回数" + String.valueOf(i));
            if (execInterrupt(interrupt)) {
                sleep(2000);
                current_screen();
                continue;
            }
            if (pictureOffSetTap(path, 0)) {
                preParts = path;
                preCertainty = 0;
                prePartsInterrupt = interrupt;
                return;
            } else {
                if (i % 5 == 0) {
                    pictureOffSetTap(preParts, preCertainty);
                    execInterrupt(prePartsInterrupt);
                }
                sleep(1000);
            }
        }
        throw new Exception("waitForTap ERRO");
    }

    /**
     * 特定の要素が出て来るまで待ってタップする
     * maxいないであれば、すべて一回ずつタップする
     *
     * @param path
     * @param max
     * @param exp_certainty
     * @return
     * @throws Exception
     */
    public int waitForMultiPartsTap(String path, int max, double exp_certainty) throws Exception {
        for (int i = 0; i < retry_base; i++) {
            debugLog("waitForMultiPartsTap 試行回数" + String.valueOf(i));
            if (pictureMultiTap(path, max, exp_certainty)) {

                preParts = path;
                preCertainty = exp_certainty;
                //現状存在する数
                int exist_num = getExistNum(path, exp_certainty);
                return exist_num >= max ? max : exist_num;
            } else {
                if (i % 5 == 0) {
                    pictureOffSetTap(preParts, preCertainty);
                    execInterrupt(prePartsInterrupt);
                }
                sleep(1000);
            }
        }
        throw new Exception("waitForTap ERRO");
    }

    /**
     * 特定の要素が出て来るまで待ってタップする
     * その際に画面最上部からインデックを指定できる
     *
     * @param path
     * @param index
     * @param exp_certainty
     * @throws Exception
     */
    public void waitForIndexTap(String path, int index, double exp_certainty) throws Exception {
        for (int i = 0; i < retry_base; i++) {
            debugLog("waitForIndexTap 試行回数" + String.valueOf(i));
            if (pictureIndexTap(path, index, exp_certainty)) {
                preParts = path;
                preCertainty = certainty_base;
                return;
            } else {
                if (i % 5 == 0) {
                    pictureOffSetTap(preParts, preCertainty);
                    execInterrupt(prePartsInterrupt);
                }
                sleep(1000);
            }
        }
        throw new Exception("waitForTap ERRO");
    }

    /**
     * 特定の要素が出つづける間はまつ
     * 途中意図しない表示が合った場合、interruptで指定されてものをタップする
     *
     * @param path
     * @param interrupt
     * @throws Exception
     */
    public void waitForLoad(String path, String interrupt[]) throws Exception {
        for (int i = 0; i < retry_base; i++) {            // 読み込みが終わっているかを確認
            if (isExist(path)) {
                break;
            } else if (i == 14) {
                throw new Exception("waitForLoad ERRO");
            } else {
                sleep(1000);
            }
        }
        boolean exist = true;
        int count = 0;
        while (exist) {
            count += 1;
            debugLog("waitForLoad 試行回数" + String.valueOf(count));
            current_screen();
            // 途中に表示されるものがあればタップする
            if (execInterrupt(interrupt)) {
                continue;
            }
            double[] offset = call_template_matching(path);
            int x = (int) offset[0];
            int y = (int) offset[1];
            double certainty = offset[2];
            debugLog("waitForLoad 正確性" + String.valueOf(certainty));
            if (certainty >= certainty_base) {
                sleep(4000);
            } else {
                exist = false;
            }
        }
    }

    /**
     * 画面のアップデートが完了するまで待つ
     *
     * @throws Exception
     */
    public void waitForScreenUpdate() throws Exception {
        current_screen();
        sleep(500);
        for (int i = 0; i < retry_base; i++) {
            if (isUpdateState()) {
                debugLog("画面のアップデートが完了");
                return;
            } else {
                sleep(1000);
            }
        }
        throw new Exception("waitForScreenUpdate ERRO");
    }

    /**
     * 特定の要素が表示されるまで待つ
     *
     * @param path
     * @throws Exception
     */
    public void waitForIsDisplay(String path) throws Exception {
        for (int i = 0; i < retry_base * 2; i++) {
            debugLog("waitForTap 試行回数" + String.valueOf(i));
            if (isExist(path)) {
                preParts = path;
                return;
            } else {
                if (i % 5 == 0) {
                    pictureOffSetTap(preParts, 0);
                }
                sleep(1000);
            }
        }
        throw new Exception("waitForLongTap ERRO");
    }

    /**
     * 対応する画像から相対的な位置へドラッグする
     *
     * @param path
     * @param rx
     * @param ry
     * @param interrupt_click
     * @param interrupt_end
     * @throws Exception
     */
    public void dragToRelativeXY(String path, int rx, int ry, String interrupt_click[],
                                 String interrupt_end[]) throws Exception {
        for (int i = 0; i < retry_base; i++) {
            debugLog("dragToRelativeXY 試行回数" + String.valueOf(i));
            current_screen();
            if (execInterrupt(interrupt_click)) {
                continue;
            }
            if (execInterruptEnd(interrupt_end)) {
                return;
            }
            double[] offset = call_template_matching(path);
            int x = (int) offset[0];
            int y = (int) offset[1];
            double certainty = offset[2];
            debugLog("dragToRelativeXY 正確性" + String.valueOf(certainty));
            if (certainty >= certainty_base) {
                driver.swipe(x, y, x + rx, y + ry, 500);
                return;
            } else {
                sleep(1000);
            }
        }
        throw new Exception("dragToRelativeXY ERRO");
    }

    /**
     * 対応する画像から絶対的な位置へドラッグする
     *
     * @param path
     * @param ax
     * @param ay
     * @param interrupt_click
     * @param interrupt_end
     * @throws Exception
     */
    public void dragToAbsoluteXY(String path, int ax, int ay, String interrupt_click[],
                                 String interrupt_end[]) throws Exception {
        for (int i = 0; i < retry_base; i++) {
            debugLog("dragToAbsoluteXY 試行回数" + String.valueOf(i));
            current_screen();
            if (execInterrupt(interrupt_click)) {
                continue;
            }
            if (execInterruptEnd(interrupt_end)) {
                return;
            }
            double[] offset = call_template_matching(path);
            int x = (int) offset[0];
            int y = (int) offset[1];
            double certainty = offset[2];
            debugLog("dragToRelativeXY 正確性" + String.valueOf(certainty));
            if (certainty >= certainty_base) {
                driver.swipe(x, y, ax, ay, 500);
                return;
            } else {
                sleep(1000);
            }
        }
        throw new Exception("dragToRelativeXY ERRO");
    }

    /**
     * 特定の画像が表示されている間、ランダムドラッッグし続ける
     *
     * @param path
     * @param tuple_rect
     * @param interrupt_click
     * @param interrupt_end
     * @throws Exception
     */
    public void dragRandam(String path, int tuple_rect[], String interrupt_click[],
                           String interrupt_end[]) throws Exception {
        //Randomクラスのインスタンス化
        Random random = new Random();
        int w = getDisplayWidth();
        int h = getDisplayHeight();

        int start_x = tuple_rect[0] < w ? tuple_rect[0] : w;
        int start_y = tuple_rect[1] < h ? tuple_rect[1] : h;
        int end_x = (tuple_rect[2] < w ? tuple_rect[2] : w) - start_x;
        int end_y = (tuple_rect[3] < h ? tuple_rect[3] : h) - start_y;

        for (int i = 0; i < retry_base * 2; i++) {
            current_screen();
            debugLog("dragRandam 試行回数" + String.valueOf(i));
            if (execInterrupt(interrupt_click)) {
                continue;
            }
            if (execInterruptEnd(interrupt_end)) {
                return;
            }
            double[] offset = call_template_matching(path);
            int x = (int) offset[0];
            int y = (int) offset[1];
            double certainty = offset[2];
            debugLog("dragRandam 正確性" + String.valueOf(certainty));
            if (certainty >= certainty_base) {
                driver.swipe(
                        (random.nextInt(end_x)) + start_x,
                        (random.nextInt(end_y)) + start_y,
                        (random.nextInt(end_x)) + start_x,
                        (random.nextInt(end_y)) + start_y, 1000);
                for (int j = 0; j < 35; j++) {
                    debugLog("dragRandam ドラッグ" + String.valueOf(j));
                    driver.swipe(
                            (random.nextInt(end_x)) + start_x,
                            (random.nextInt(end_y)) + start_y,
                            (random.nextInt(end_x)) + start_x,
                            (random.nextInt(end_y)) + start_y, 300);
                }
                return;
            } else {
                tap(w / 2, h / 2);
                sleep(500);
            }
        }
        throw new Exception("dragRandam ERRO");
    }


    /**
     * 特定の画像が表示されるまで一定感覚で画面中央をタップし続ける
     *
     * @param path
     * @throws Exception
     */
    public void barrageForTap(String path) throws Exception {
        int w = getDisplayWidth();
        int h = getDisplayHeight();

        for (int i = 0; i < retry_base * 4; i++) {
            current_screen();
            debugLog("barrageForTap 試行回数" + String.valueOf(i));

            if (isExist(path)) {
                debugLog("連打終了");
                preParts = path;
                return;
            } else {
                if (i % 10 == 0) {
                    pictureOffSetTap(preParts, 0);
                }
                tap(w / 2, h / 2);
            }
            sleep(200);
        }
        throw new Exception("barrageForTap ERRO");
    }


    /**
     * 特定の画像が表示されるまで一定感覚で画面中央をタップし続ける
     *
     * @param path
     * @param interrupt
     * @throws Exception
     */
    public void barrageForTap(String path, String interrupt[]) throws Exception {
        int w = getDisplayWidth();
        int h = getDisplayHeight();

        for (int i = 0; i < retry_base * 4; i++) {
            current_screen();
            debugLog("barrageForTap 試行回数" + String.valueOf(i));
            if (execInterrupt(interrupt)) {
                continue;
            }
            if (isExist(path)) {
                debugLog("連打終了");
                preParts = path;
                prePartsInterrupt = interrupt;
                return;
            } else {
                if (i % 10 == 0) {
                    pictureOffSetTap(preParts, 0);
                    execInterrupt(prePartsInterrupt);
                }
                tap(w / 2, h / 2);
            }
            sleep(200);
        }
        throw new Exception("barrageForTap ERRO");
    }

    /**
     * 特定の画像が表示間、一定感覚で特定画像をタップし続ける
     *
     * @param path
     * @throws Exception
     */
    public void barrageExitForTap(String path) throws Exception {
        for (int i = 0; i < retry_base * 4; i++) {
            current_screen();
            debugLog("barrageExitForTap 試行回数" + String.valueOf(i));

            if (pictureOffSetTap(path, 0)) {
                if (i % 10 == 0) {
                    pictureOffSetTap(preParts, 0);
                }
                sleep(200);
            } else {
                preParts = path;
                debugLog("連打終了");
                return;
            }
        }
        throw new Exception("barrageExitForTap ERRO");
    }

    /**
     * 特定の画像が表示間、一定感覚で特定画像をタップし続ける
     *
     * @param path
     * @param interrupt
     * @throws Exception
     */
    public void barrageExitForTap(String path, String interrupt[]) throws Exception {
        for (int i = 0; i < retry_base * 4; i++) {
            current_screen();
            debugLog("barrageExitForTap 試行回数" + String.valueOf(i));
            if (execInterrupt(interrupt)) {
                continue;
            }
            if (pictureOffSetTap(path, 0)) {
                sleep(200);
            } else {
                debugLog("連打終了");
                return;
            }
        }
        throw new Exception("barrageExitForTap ERRO");
    }

    /**
     * 特定の画像が表示されるまで下にスクロールする
     *
     * @param path
     * @throws Exception
     */
    public void scrollToDown(String path) throws Exception {
        int w = getDisplayWidth();
        int h = getDisplayHeight();

        current_screen();
        for (int i = 0; i < retry_base * 4; i++) {
            driver.swipe(w / 2, h * 2 / 3, w / 2, h * 1 / 3, 800);
            current_screen();
            waitForScreenUpdate();
            if (isExist(path)) {
                debugLog("下スクロール完了  ");
                return;
            }
            throw new Exception("scrollToDown ERRO");
        }
    }

    public void scroll() {
        int w = getDisplayWidth();
        int h = getDisplayHeight();

        driver.swipe(w / 2, h * 2 / 3, w / 2, h * 1 / 3, 800);
    }

    public int getDisplayWidth() {
        return driver.manage().window().getSize().getWidth();
    }

    public int getDisplayHeight() {
        return driver.manage().window().getSize().getHeight();
    }

    public void pressHomeKey() {
        press(AndroidKeyCode.HOME);
    }

    public void pressAppSwitchkey() {
        press(AndroidKeyCode.KEYCODE_APP_SWITCH);
    }
}
