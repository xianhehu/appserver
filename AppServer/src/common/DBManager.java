package common;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.log4j.Logger;

import com.mysql.jdbc.Connection;
import com.mysql.jdbc.Statement;

import appserver.Main;

public class DBManager {
	private Connection   conn = null;
	private Statement    sql;

	private String url;
	private String user;
	private String passwd;
	//private static Logger logger = Logger.getLogger("appserver");

	public DBManager(String url, String user, String pass) {
		this.user = user;
		this.url = url;
		this.passwd = pass;

		start();

		connect();
	}

	public boolean start() {
		try{
            //调用Class.forName()方法加载驱动程序
            Class.forName("com.mysql.jdbc.Driver");
            System.out.println("成功加载MySQL驱动！");

            return true;
        }catch(ClassNotFoundException e){
            Main.logger.error("找不到MySQL驱动!", e);

            return false;
        }
	}

	public boolean connect() {
		try {
            conn = (Connection) DriverManager.getConnection(url, user, passwd);
            //创建一个Statement对象
            Statement stmt = (Statement) conn.createStatement(); //创建Statement对象
            sql = stmt;

            Main.logger.debug("成功连接到数据库！");
        } catch (SQLException e){
            Main.logger.error("连接到数据库失败！", e);

            return false;
        }

		return true;
	}

	public ResultSet query(String state) {
		try {
			if (conn == null || sql.isClosed() || conn.isClosed()) {
				connect();
			}

			sql.execute(state);

			return sql.getResultSet();
		} catch (Exception e) {
//			e.printStackTrace();
//			System.out.print(state+"执行失败！");
			Main.logger.error(state+"执行失败！", e);
			connect();

			return null;
		}
	}
}
