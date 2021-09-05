//package tcpipchapter3;

import java.awt.List;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.sql.*;

public class App {
 /**
  * @param args
  */
 //设置端口号/
 public static int portNo=3333;
     //Mysql连接的初始设置
 public static Connection con = null;
 public static     String driver = "com.mysql.jdbc.Driver";
 public static     String url = "jdbc:mysql://localhost:3306/curriculum_design";
 public static     String user = "root";
 public static     String password = "@Ep2c8c5509a";  

 public static String readSQLItems(){
     String cycle_line = "";
     //-----------------------------------------------------------------------------------------
    //把数据库内容读出来，发送回客户端
    try {
        //加载驱动程序
        Class.forName(driver);
       //getConnection()方法，连接MySQL数据库！！
        con = DriverManager.getConnection(url,user,password);
        if(!con.isClosed())
            System.out.println("Succeeded connecting to the Database!");        
        //创建statement类对象，用来执行SQL语句！！
        Statement statement = con.createStatement();
  
        //要执行的SQL语句
        String sql = "select * from car_cycle_data"; //"select * from car_cycle_data order by id DESC limit 1"; //"select *   from car_cycle_data where id=(select max(id) from car_cycle_data)"; //查找最后一行记录，也就是最新插入的一行记录        
        //3.ResultSet类，用来存放获取的结果集！！
        ResultSet rs = statement.executeQuery(sql);
        ResultSetMetaData md = rs.getMetaData();//获取键名
        int columnCount = md.getColumnCount();//获取行的数量
       while(rs.next())  { 
            for (int i = 1; i <= columnCount; i++) {
                cycle_line = cycle_line+" "+ rs.getString(i);//获取键名及值
            }
            cycle_line = cycle_line+"\n";
       }       
        con.close();
    } 
    catch(SQLException e) {
        e.printStackTrace();  
        }
        catch (Exception e) {
            e.printStackTrace();
        }
            finally{
                System.out.println("数据库表读取完成！！");
            }
    return cycle_line;
 }

 public static void main(String[] args) throws IOException {

   //-----------------------------------------------------------------------------------------
  //初始化serverSocket类
  ServerSocket s=new ServerSocket(portNo,10,InetAddress.getByName("172.24.130.253"));
  System.out.println("The Server is starting...");
  //建立socket连接(阻塞，直到有客户端连接)
  Socket socket=s.accept();

  //接收数据
  try{
    //构造输入流缓存
    BufferedReader bufReader=new BufferedReader(new InputStreamReader(socket.getInputStream()));
    PrintWriter out=new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())),true);
    String time=new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date());
    while(true){
        //按行读取输入内容
        String strLine=bufReader.readLine();
        //如果收到byebye则退出循环
        if(strLine.equals("query all")){
            out.println("Results readed from cloud database: "+ readSQLItems());
            break;
        }
    System.out.println("In the Server reveived: "+strLine);

    //-----------------------------------------------------------------------------------------
    //把strline内容写入数据库
    try {
        //加载驱动程序
        Class.forName(driver);
       //getConnection()方法，连接MySQL数据库！！
        con = DriverManager.getConnection(url,user,password);
        if(!con.isClosed())
            System.out.println("Succeeded connecting to the Database!");        
        //要执行的SQL语句
        String sql = "INSERT INTO car_cycle_data(id,encoder_right,encoder_left,acceleration_x,acceleration_y,acceleration_z,gyroscope_x,gyroscope_y,gyroscope_z,magnetometer_x,magnetometer_y,magnetometer_z,electromagnetic_right,electromagnetic_center,electromagnetic_left,off_center,steering_gear_control,motor_control_left,motor_control_right,P_value,I_value,D_value) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        PreparedStatement pstm = con.prepareStatement(sql);
        String[] field = strLine.split("\\s+"); //按空格符分割接收到的字符串
        for (int i=0;i<field.length;i++){
            pstm.setString(i+1, field[i]);
        }
        pstm.executeUpdate();
        pstm.close();
        con.close();
    } 
    catch(SQLException e) {
        e.printStackTrace();  
        }
        catch (Exception e) {
            e.printStackTrace();
        }
            finally{
                System.out.println("数据库数据成功写入！！");
            }

    //向客户端发送接收到的数据
    System.out.println("The server send the received msg to the client...");
    out.println("Successfully writed into cloud database: "+ strLine +" and The time is:"+time);
   }
  }
  catch(Exception e){
   e.printStackTrace();
  }
  finally{
   System.out.println("close the Server socket and the io");
   //关闭socket,断开连接
   socket.close();
   s.close();
  }

 }

}
