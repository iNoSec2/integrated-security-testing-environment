package okuken.iste.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.apache.ibatis.session.SqlSession;

public class SqlUtil {

	private static final DateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
	public static final String now() {
		return timestampFormat.format(Calendar.getInstance().getTime());
	}

	public static final int loadGeneratedId(SqlSession session) {
		try (Statement stmt = session.getConnection().createStatement()) {
			try(ResultSet rs = stmt.executeQuery("SELECT LAST_INSERT_ROWID()")){ //SQLite
				rs.next();
				return rs.getInt(1);
			}
		} catch (SQLException e) {
			throw new RuntimeException("fail to load generated id value.", e);
		}
	}

}