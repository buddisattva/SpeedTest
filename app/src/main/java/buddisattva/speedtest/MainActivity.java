package buddisattva.speedtest;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

//import akinaru撰寫的JSpeedTest Library來使用
import fr.bmartel.speedtest.ISpeedTestListener;
import fr.bmartel.speedtest.SpeedTestError;
import fr.bmartel.speedtest.SpeedTestReport;
import fr.bmartel.speedtest.SpeedTestSocket;

public class MainActivity extends AppCompatActivity {

    TextView labelNetworkStatus;
    String stringNetworkStatus;
    TextView labelDownloadRate;
    double downloadRate;
    TextView labelUploadRate;
    double uploadRate;

    //控制AsyncTask的onProgressUpdate生命週期時，要更新的是下載速率/下載時間的TextView還是上傳速率/上傳時間的
    boolean isDownload = false;
    boolean isUpload = false;

    TextView labelDownloadTime;
    TextView labelUploadTime;
    TextView labelTotalTime;
    double downloadTime;
    double uploadTime;
    double totalTime;
    ProgressBar progressBarSpeedTest;
    Button buttonStart;

    //讓按鈕在測速中不能按以免誤按一直加入新的AsyncTask拖慢速度
    boolean isFinished;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //利用ConnectivityManager物件來看目前網路是WiFi還是Mobile Network
        ConnectivityManager connectivityManager = (ConnectivityManager)getSystemService(this.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        boolean isWifiConn = networkInfo.isConnected();
        networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        boolean isMobileConn = networkInfo.isConnected();

        if(isWifiConn)
            stringNetworkStatus = "WiFi Network";
        else if(isMobileConn)
            stringNetworkStatus = "Mobile Network";
        else
            stringNetworkStatus = "No Network";
        //網路狀態觀測部分到上面為止

        //UI物件實體化部分
        labelNetworkStatus = (TextView)findViewById(R.id.labelNetworkStatus);
        labelNetworkStatus.setText(stringNetworkStatus);

        labelDownloadRate = (TextView)findViewById(R.id.labelDownloadRate);
        labelUploadRate = (TextView)findViewById(R.id.labelUploadRate);
        labelDownloadTime = (TextView)findViewById(R.id.labelDownloadTime);
        labelUploadTime = (TextView)findViewById(R.id.labelUploadTime);
        labelTotalTime = (TextView)findViewById(R.id.labelTotalTime);

        progressBarSpeedTest = (ProgressBar)findViewById(R.id.progressBarSpeedTest);
        //設定progressBar大小
        progressBarSpeedTest.setScaleX(3f);
        progressBarSpeedTest.setScaleY(4f);

        //按鈕實體化並且註冊一個OnClickListener，按下去開始執行測速的AsyncTask
        buttonStart = (Button)findViewById(R.id.buttonStart);
        buttonStart.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                //測速結果部分先清空，非首次測速時可以把上次的結果清空
                labelDownloadRate.setText("");
                labelUploadRate.setText("");
                labelDownloadTime.setText("");
                labelUploadTime.setText("");
                labelTotalTime.setText("");

                //執行測速的AsyncTask，同時把按鈕鎖住
                new SpeedTestTask().execute();
                isFinished = false;
                buttonStart.setEnabled(false);
                buttonStart.setText("測速中");
            }
        });

    }

    //測速用的AsyncTask
    class SpeedTestTask extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... params) {

            final SpeedTestSocket speedTestSocket = new SpeedTestSocket();

            //JSpeedTest有自己的生命週期，註冊一個Listener監看生命週期，依照不同生命週期做不同事
            speedTestSocket.addSpeedTestListener(new ISpeedTestListener() {

                @Override //Download測速結束時要做啥
                public void onDownloadFinished(SpeedTestReport report) {
                    //Log.v("speedtest", "[DL FINISHED] rate in octet/s : " + report.getTransferRateOctet());
                    //Log.v("speedtest", "[DL FINISHED] rate in bit/s   : " + report.getTransferRateBit());

                    //BigDecimal轉成double
                    downloadRate = report.getTransferRateBit().doubleValue();
                    //回報時間(Unix Timestamp) - 開始時間(Unix Timestamp) = 測速經歷時間。再除以1000把ms轉成s
                    downloadTime = ((double)report.getReportTime() - (double)report.getStartTime()) / 1000;
                    totalTime = uploadTime + downloadTime;
                    //AsyncTask中的onProgressUpdate生命週期，需要用publishProgress方法來傳值過去更新UI內容
                    publishProgress(String.format("%.3f bit/s" , downloadRate), downloadTime + "秒" ,
                            String.format("%.3f秒" ,totalTime), "100");

                    //Download測速完成，換Upload
                    speedTestSocket.startUpload("2.testdebit.info", "/", 1000000);
                }

                @Override
                public void onDownloadError(SpeedTestError speedTestError, String errorMessage) {
                    // Download測速出錯時要做啥
                }

                @Override // Upload測速結束時要做啥
                public void onUploadFinished(SpeedTestReport report) {
                    //Log.v("speedtest", "[UL FINISHED] rate in octet/s : " + report.getTransferRateOctet());
                    //Log.v("speedtest", "[UL FINISHED] rate in bit/s   : " + report.getTransferRateBit());

                    //透過這個布林值讓按鈕變成可以按，在onProgressUpdate週期中實作
                    isFinished = true;

                    uploadRate = report.getTransferRateBit().doubleValue();
                    uploadTime = ((double)report.getReportTime() - (double)report.getStartTime()) / 1000;
                    totalTime = uploadTime + downloadTime;
                    publishProgress(String.format("%.3f bit/s" , uploadRate), uploadTime + "秒" ,
                            String.format("%.3f秒" ,totalTime), 100 + "");
                }

                @Override
                public void onUploadError(SpeedTestError speedTestError, String errorMessage) {
                    //Upload測速出錯時要做啥
                }

                @Override //Download測速中要做啥
                public void onDownloadProgress(float percent, SpeedTestReport report) {
                    //透過這兩個布林值，讓onProgressUpdate週期中只會更新下載部分UI
                    isDownload = true;
                    isUpload = false;

                    //Log.v("speedtest", "[DL PROGRESS] progress : " + percent + "%");
                    //Log.v("speedtest", "[DL PROGRESS] rate in octet/s : " + report.getTransferRateOctet());
                    //Log.v("speedtest", "[DL PROGRESS] rate in bit/s   : " + report.getTransferRateBit());
                    downloadRate = report.getTransferRateBit().doubleValue();
                    downloadTime = ((double)report.getReportTime() - (double)report.getStartTime()) / 1000;
                    totalTime = uploadTime + downloadTime;
                    publishProgress(String.format("%.3f bit/s" , downloadRate), downloadTime + "秒",
                            String.format("%.3f秒" ,totalTime),(int)percent + "");
                }

                @Override //Upload測速中要做啥
                public void onUploadProgress(float percent, SpeedTestReport report) {
                    //透過這兩個布林值，讓onProgressUpdate週期中只會更新上傳部分UI
                    isDownload = false;
                    isUpload = true;

                    //Log.v("speedtest", "[UL PROGRESS] progress : " + percent + "%");
                    //Log.v("speedtest", "[UL PROGRESS] rate in octet/s : " + report.getTransferRateOctet());
                    //Log.v("speedtest", "[UL PROGRESS] rate in bit/s   : " + report.getTransferRateBit());
                    uploadRate = report.getTransferRateBit().doubleValue();
                    uploadTime = ((double)report.getReportTime() - (double)report.getStartTime()) / 1000;
                    totalTime = uploadTime + downloadTime;
                    publishProgress(String.format("%.3f bit/s" , uploadRate), uploadTime + "秒",
                            String.format("%.3f秒" ,totalTime), (int)percent + "");
                }

                @Override
                public void onInterruption() {
                    // AsyncTask被強迫終止時要做啥
                }
            });
            //開始進行Download測速，Upload測速的啟動指令放在onDownloadFinished生命週期中
            speedTestSocket.startDownload("2.testdebit.info", "/fichiers/1Mo.dat");

            return null;
        }
        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);

            //Download測速時，只更新Download部分UI
            if(isDownload) {
                labelDownloadRate.setText(values[0]);
                labelDownloadTime.setText(values[1]);
                labelTotalTime.setText(values[2]);
                progressBarSpeedTest.setProgress(Integer.parseInt(values[3]));
            }
            //Upload測速時，只更新Upload部分UI
            else if(isUpload) {
                labelUploadRate.setText(values[0]);
                labelUploadTime.setText(values[1]);
                labelTotalTime.setText(values[2]);
                progressBarSpeedTest.setProgress(Integer.parseInt(values[3]));
            }

            //測速全部完成時，讓按鈕再度可以按
            if(isFinished) {
                buttonStart.setText("再測一次");
                buttonStart.setEnabled(true);
            }
        }
    }
}