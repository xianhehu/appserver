import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.mysql.jdbc.Connection;
import com.mysql.jdbc.Statement;

public class DBManager {
	private Connection   conn;
	private Statement    sql;
	
	static public String url;
	static public String user;
	static public String passwd;

	public boolean start() {
		// TODO Auto-generated constructor stub
		try{
            //����Class.forName()����������������
            Class.forName("com.mysql.jdbc.Driver");
            System.out.println("�ɹ�����MySQL������");
            
            return true;
        }catch(ClassNotFoundException e1){
            System.out.println("�Ҳ���MySQL����!");
            e1.printStackTrace();
            
            return false;
        }
	}

	public boolean connect() {
		// TODO Auto-generated method stub
		try {
            conn = (Connection) DriverManager.getConnection(url, user, passwd);
            //����һ��Statement����
            Statement stmt = (Statement) conn.createStatement(); //����Statement����
            sql = stmt;
            System.out.print("�ɹ����ӵ����ݿ⣡");
        } catch (SQLException e){
            e.printStackTrace();
            System.out.print("���ӵ����ݿ�ʧ�ܣ�");
            return false;
        }
		
		return true;
	}

	public ResultSet query(String state) {
		try {
			if (sql.isClosed() || conn.isClosed()) {
				connect();
			}
			
			sql.execute(state);
			return sql.getResultSet();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.print(state+"ִ��ʧ�ܣ�");
			connect();
            return null;
		}
	}
}