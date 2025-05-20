package zhonghesheji;

import java.lang.String;
import java.net.*;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.io.*;
//import java.net.*;


public class App {
 /**
  * @param args
  */
  private static Socket remoteSocket;
  private static ServerSocket remoteServer;
  public static BufferedReader remoteBufReader;
  public static  PrintWriter remoteOut;

  //设置端口号/
  public static int portNo=3333;
  public static String cycledata;
  public static boolean saveEnable = false;
  public static boolean relayEnable = false;
  public static void receivedCycledata(String strLine,boolean saveEnable, boolean relayEnable){
        App.cycledata = strLine;
        App.saveEnable = saveEnable;
        App.relayEnable = relayEnable;
        System.out.println("Java Server reveived: "+strLine);  
  }
  public static PrintWriter getRemoteOut(){
    return App.remoteOut;
  }

  public static String getCycleData(){
    return App.cycledata;
  }

  public static boolean getSaveEnable(){
    return App.saveEnable;
  }
  public static boolean getRelayEnable(){
    return App.relayEnable;
  }

  public static void clearSaveEnable(){
    App.saveEnable=false;
  }
  public static void clearRelayEnable(){
    App.relayEnable=false;
  }

 public static void main(String[] args) throws IOException {

    //-----------------------------------------------------------------------------------------
    try{
      RelaySaveThreads relaySaveThreads = new RelaySaveThreads();
      relaySaveThreads.startThreads();      
      //构造输入流缓存
      remoteServer=new ServerSocket(portNo,10,InetAddress.getByName("172.24.130.253"));
      remoteSocket=remoteServer.accept();//建立socket连接(阻塞，直到有客户端连接)
      System.out.println("The Remote Server for Matlab is starting...");                                 
      remoteBufReader=new BufferedReader(new InputStreamReader(remoteSocket.getInputStream()));
      remoteOut=new PrintWriter(new BufferedWriter(new OutputStreamWriter(remoteSocket.getOutputStream())),true);
      while(true){   
          String strLine=remoteBufReader.readLine(); //按行读取输入内容
          if(strLine!=null){
              App.receivedCycledata(strLine,true,true); //返回小车数据、saveEnable=T、relayEnable=T；
              Lock lock = relaySaveThreads.getLock();
              lock.lock();
              Condition saveLock = relaySaveThreads.getrelayLock();
              saveLock.signalAll();
              Condition relayLock = relaySaveThreads.getsaveLock();
              relayLock.signalAll();
              lock.unlock();
          }
      }
  }
  catch(Exception e){
      e.printStackTrace();
      //关闭socket,断开连接
      try {
          remoteSocket.close();
      } catch (IOException e1) {
          e1.printStackTrace();
      }
      try {
          remoteServer.close();
      } catch (IOException e1) {
          e1.printStackTrace();
      }                    
  }
 }
}

class  RelaySaveThreads {
  //初始化本地服务器

  public String  PID=null;
  public InputStream inPID;

  public String getPID(){
    return this.PID;
  }

  private Lock lock = new ReentrantLock();
  private Condition saveLock = lock.newCondition();
  private Condition relayLock = lock.newCondition();
  private LocalServerThread localServerThread;
  private AccessSQLThread accessSQLThread;
  private ReceivePIDThread receivePIDThread;

  public Condition getsaveLock(){
    return this.saveLock;
  }
  public Condition getrelayLock(){
    return this.relayLock;
  }
  public Lock getLock(){
    return this.lock;
  }  

  public void startThreads(){
    localServerThread = new LocalServerThread();
    localServerThread.start();
    
    //写入SQL数据库
    accessSQLThread = new AccessSQLThread();
    accessSQLThread.start(); 

  }

  private class ReceivePIDThread extends Thread {      

    @Override
    public  void run(){
      while(true){
          try{                  
            byte[] buf = new byte[256];
            int len=0;
            while((len = inPID.read(buf)) != -1){
              System.out.println(new String(buf,0,len));
              PrintWriter remoteOut = App.getRemoteOut();
              remoteOut.write(new String(buf,0,len));
              remoteOut.flush();
              System.out.println("PID back to Matlab is: "+new String(buf,0,len));
            }
          } catch (Exception e) {  
                System.out.println("接收PID线程异常:" + e.getMessage());   
            }   
      }  
    }
  }


 private class LocalServerThread extends Thread {      
   private Socket localSocket;
   private ServerSocket localServer;
   private String strLine;

   @Override
   public  void run(){
           //发送数据 
       try{
           localServer=new ServerSocket(8003,10,InetAddress.getByName("127.0.0.1"));
           localSocket=localServer.accept();//建立socket连接(阻塞，直到有NodeJS客户端连接)
           PrintWriter out=new PrintWriter(new BufferedWriter(new OutputStreamWriter(localSocket.getOutputStream())),true);             
           inPID = localSocket.getInputStream();
           receivePIDThread = new ReceivePIDThread();
           receivePIDThread.start();
           System.out.println("The Local Server for nodeJS is starting...");   
           while(true){
              lock.lock();
              relayLock.await();
               if(App.getRelayEnable()){
                   App.clearRelayEnable();
                   strLine = App.getCycleData();
                   out.write(strLine);
                   out.flush();
                   System.out.println("relay to NodeJS Web successful: "+strLine);             
               }
              lock.unlock();
           }      
       }
       catch(Exception e){
           e.printStackTrace();
           try {
            localSocket.close();
          } catch (IOException e1) {
            e1.printStackTrace();
          }    
           try {
            localServer.close();
          } catch (IOException e1) {
            e1.printStackTrace();
          }              
       }
     }
   }

   private class AccessSQLThread extends Thread { 

    //Mysql连接的初始设置
    private java.sql.Connection con = null;
    private String driver = "com.mysql.jdbc.Driver";
    private String url = "jdbc:mysql://localhost:3306/curriculum_design";
    private String user = "root";
    private String password = "@Ep2c8c5509a";  

    private String strLine;
          
   @Override
   public void run(){
       while(true){         
         try {     //把strline内容写入数据库
             lock.lock();
             saveLock.await();
             if(App.getSaveEnable()){
                 App.clearSaveEnable(); 
                 Class.forName(driver);//加载驱动程序
                 con = DriverManager.getConnection(url,user,password);//getConnection()方法，连接MySQL数据库！！  
                 //要执行的SQL语句
                 String sql = "INSERT INTO car_cycle_data(id,encoder_right,encoder_left,acceleration_x,acceleration_y,acceleration_z,gyroscope_x,gyroscope_y,gyroscope_z,magnetometer_x,magnetometer_y,magnetometer_z,electromagnetic_right,electromagnetic_center,electromagnetic_left,off_center,steering_gear_control,motor_control_left,motor_control_right,P_value,I_value,D_value) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
                 PreparedStatement pstm = con.prepareStatement(sql);
                 strLine = App.getCycleData();
                 String[] field = strLine.split(","); //("\\s+"); //按空格符分割接收到的字符串
                 for (int i=0;i<field.length;i++){
                     pstm.setString(i+1, field[i]);
                 }
                 pstm.executeUpdate();
                 pstm.close();
                 con.close();   
                 System.out.println("save to MySQL successful: "+strLine);   
             }
             lock.unlock();
         } 
         catch(SQLException e) {
             e.printStackTrace();            
             }
         catch (Exception e) {
             e.printStackTrace();
         }   
       }
   }    
 }
}
