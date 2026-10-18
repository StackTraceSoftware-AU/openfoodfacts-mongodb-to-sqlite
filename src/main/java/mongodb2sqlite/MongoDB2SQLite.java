/*
 *******************************************************
 * MongoDB2SQLite - Export MongoDB documents to SQLite *
 *******************************************************
 *  Version History:
 *	  23/4/25:	v1.0	First version
 *	  16/10/26:	v1.1	Updated to fix identified issues. See Release Notes.
 *
 *	Usage:
 *	  MongoDB2SQLite <database.collection> <sqlite-file> <table>
 *	
 *	Example:
 *	  MongoDB2SQLite off.products /path/to/off_sqlite.db products
 *	
 *	The MongoDB server must be running locally.
 *	The SQLite database will be created if it does not exist.
 *	If the SQLite database does exist then the data will be added to the specified table
 */

package mongodb2sqlite;

import java.nio.file.InvalidPathException;
import java.nio.file.Paths;

import org.apache.commons.lang3.StringUtils;

import com.mongodb.MongoClientException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

import org.bson.Document;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;


public class MongoDB2SQLite {
	
	final static String TAG = "MongoDB2SQLite";
	final static String helpText =
	        "MongoDB2SQLite - Export MongoDB documents to SQLite\n\n" +
	        "Usage: MongoDB2SQLite <database.collection> <sqlite-file> <table>\n" +
	        "Example: MongoDB2SQLite off.products /path/to/off_sqlite.db products\n\n" +
	        "The MongoDB server must be running locally.\n" +
	        "The SQLite database will be created if it does not exist.\n";

	static String mongoConnection = "mongodb://localhost";	//This procedure is only designed to run using a local MongoDB server
	static String sourceDatabase="";
	static String sourceCollection="";
	static Integer batchCounter=0;
	static Integer batchesWritten=0;
	
	public static void main(String[] args) {
		System.out.println(TAG +": Started at "+getCurrentTime());
		
		//Command line arguments
		String mongo_source="";
		String sql_dest="";
		String sql_table="";
		
		MongoClient mongoClient = null;
		Connection sqlconn = null;
		PreparedStatement pstmt = null;
		MongoCursor<Document> cursor = null;
		
		try {
			//Get args
	        if (args.length == 0) {
	        	//Show help text
	        	System.out.println(helpText);
	        	return;
	        } else if (args[0].equals("--help")) {
	        	//Show help text
	        	System.out.println(helpText);
	        	return;	        	
	        } else if (args.length != 3) {
	        	System.out.println("Insufficient arguments. Use --help for more");
	        	return;
	        } else {
            	mongo_source = args[0];
            	sql_dest = args[1];
            	sql_table = args[2];
	        }
	            
	        //Now validate the args passed
            if ( !validateSource(mongo_source) ) {
            	System.out.println("Mongo DB Source must be in the format: db.collection");
            	return;
            }
            if ( !validateDestination(sql_dest)) {
            	System.out.println("SQLite destination must be in the format: /path/database.db");
            	return;		            	
            }
            if (!validateTableName(sql_table)) {
                System.out.println("Invalid SQLite table name: " + sql_table);
                return;
            }
			
			//Open MongoDB connection. Only local connections are supported by this version
	        mongoClient = MongoClients.create(mongoConnection);
            
        	//Get source database and collection
            MongoDatabase mongoDatabase = mongoClient.getDatabase(sourceDatabase);
            MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(sourceCollection);
		
			//Open SQLite connection. New database will be created if does not exist
            sqlconn = DriverManager.getConnection("jdbc:sqlite:"+sql_dest);
            sqlconn.setAutoCommit(false);
            
            //Check if the table exists, create if required, and report result
            String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";

            try (PreparedStatement checkStmt = sqlconn.prepareStatement(sql)) {
                checkStmt.setString(1, sql_table);

                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("Table '" + sql_table + "' already exists. Data will be appended.");
                    } else {
                        System.out.println("Table '" + sql_table + "' does not exist.");

                        if (createTable(sqlconn, sql_table)) {
                            System.out.println("Table '" + sql_table + "' created OK.");
                        } else {
                            System.out.println("Unable to create destination table.");
                            return;
                        }
                    }
                }
            }
            			
			//Counters for export
            int processed=0;
            int exported=0;
            int rejected=0;
            int errors=0;
            
            //Create a PreparedStatement. Statements will be added and executed in batches
			String insertSQL = "INSERT INTO "+sql_table+" \n"+
					"(code,product_name,quantity,brands,labels_tags,countries_tags,ingredients_tags,allergens_tags,traces_tags,additives_tags) \n"+
					" VALUES (?,?,?,?,?,?,?,?,?,?)";

			pstmt = sqlconn.prepareStatement(insertSQL);
            
			//Export all documents in the MongoDB Collection
            cursor = mongoCollection.find().iterator();

            while (cursor.hasNext()) {
            	Document doc = cursor.next();
            	//Need to do some data quality checks here
            	String code=doc.getString("code");
            	String product_name=doc.getString("product_name");
            	if (code == null || code.isEmpty() || code.length() > 13) {
            		rejected++;
            		System.out.println("Rejected code="+code);
            	} else if (product_name == null || product_name.equals("") || 
            			product_name.equals("x") || containsNonEnglishCharacters(product_name)) {
            		rejected++;
            		System.out.println("Rejected code="+code);
            	} else if (exportDocument(sqlconn, pstmt, doc)) {
            		exported++;
            	} else {
            		errors++;
            		//Halt after n errors
            		if (errors > 0)
            			break;
            	}
            	processed++;
            	//For development and testing: Halt after n processed
            	//if (processed == 10000)
            		//break;

            }
            System.out.println(processed+" documents processed / "+exported+" exported / "+rejected+" rejected / "+errors+" errors");
 	

            //Execute remaining statements only if no errors occurred
            if (errors == 0) {
            	int[] updateCounts = pstmt.executeBatch();

            	for (int count : updateCounts) {
            	    if (count == Statement.EXECUTE_FAILED) {
            	        throw new SQLException("Final batch insert failed");
            	    }
            	}
            	sqlconn.commit();
            	
            } else {
                sqlconn.rollback();
                System.out.println("Export stopped due to an error.");
            }
	        
		} catch (MongoClientException me) {
        	me.printStackTrace();	
		} catch (SQLException sqe) {
			sqe.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		    //Close resources even if an exception occurred
		    if (cursor != null) {
		        try {
		            cursor.close();
		        } catch (Exception e) {
		            e.printStackTrace();
		        }
		    }

		    if (pstmt != null) {
		        try {
		            pstmt.close();
		        } catch (SQLException e) {
		            e.printStackTrace();
		        }
		    }

		    if (sqlconn != null) {
		        try {
		            if (!sqlconn.isClosed()) {
		                if (!sqlconn.getAutoCommit()) {
		                    sqlconn.rollback();
		                }
		            }
		        } catch (SQLException e) {
		            e.printStackTrace();
		        } finally {
		            try {
		                sqlconn.close();
		            } catch (SQLException e) {
		                e.printStackTrace();
		            }
		        }
		    }

		    if (mongoClient != null) {
		        try {
		            mongoClient.close();
		        } catch (Exception e) {
		            e.printStackTrace();
		        }
		    }

		    System.out.println(TAG + ": Finished at " + getCurrentTime());
		} //finally
		
	}	//main
	
	//We only allow English characters, Western European accents and Nordic characters
    public static boolean containsNonEnglishCharacters(String input) {
        //Regular expression to match non-English characters, including accented characters and some extended Latin characters
        Pattern pattern = Pattern.compile("[^\\p{ASCII}\\p{InLatin_1_Supplement}\\p{InLatin_Extended_A}\\p{InLatin_Extended_B}]");
        return pattern.matcher(input).find();
    }
    
    //SQLite destination table name must be a simple SQL identifier
    private static boolean validateTableName(String tableName) {
        return tableName != null &&
               tableName.matches("[A-Za-z_][A-Za-z0-9_]*");
    }
	
	//Create table in destination SQLite database.
	private static boolean createTable(Connection sqlconn, String tableName) {
 
		String createTableSQL = "CREATE TABLE IF NOT EXISTS "+tableName+" (\n" +
                "	id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
				"	code TEXT,\n" +
				"	product_name TEXT,\n" +
				"	quantity TEXT,\n" +
				"	brands TEXT,\n" +
				"	labels_tags TEXT,\n" +
				"	countries_tags TEXT,\n" +
				"	ingredients_tags TEXT,\n" +
				"	allergens_tags TEXT,\n" +
				"	traces_tags TEXT,\n" +
				"	additives_tags TEXT,\n" +
                "	created_at DATETIME DEFAULT CURRENT_TIMESTAMP\n" +
                ");";
		
		try {
			Statement stmt = sqlconn.createStatement();
            stmt.execute(createTableSQL);
            sqlconn.commit();
            stmt.close();
            return true;
		} catch (SQLException sqe) {
			sqe.printStackTrace();
			return false;		
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}	//createTable
	
	//Export document to SQLite table
	private static boolean exportDocument(Connection sqlconn, PreparedStatement pstmt, Document doc) {
				
		try {
			//Get fields from doc
			String code=doc.getString("code");
			String product_name=doc.getString("product_name");
			String quantity=doc.getString("quantity");
			String brands=doc.getString("brands");

			List<String> labels_tags = (List<String>) doc.get("labels_tags");
			List<String> countries_tags = (List<String>) doc.get("countries_tags");
			List<String> ingredients_tags = (List<String>) doc.get("ingredients_tags");
			List<String> allergens_tags = (List<String>) doc.get("allergens_tags");
			List<String> traces_tags = (List<String>) doc.get("traces_tags");
			List<String> additives_tags = (List<String>) doc.get("additives_tags");
			
			if (code != null) {pstmt.setString(1, code);} else {pstmt.setString(1, "");}
			if (product_name != null) {pstmt.setString(2, product_name);} else {pstmt.setString(2, "");}
			if (quantity != null) {pstmt.setString(3, quantity);} else {pstmt.setString(3, "");}
			if (brands != null) {pstmt.setString(4, brands);} else {pstmt.setString(4, "");}
			//Tag fields contain multi values so must be handled differently
			if (labels_tags != null) { setMultiStrings(pstmt, 5, labels_tags);} else {pstmt.setString(5, "");}
			if (countries_tags != null) { setMultiStrings(pstmt, 6, countries_tags);} else {pstmt.setString(6, "");}
			if (ingredients_tags != null) { setMultiStrings(pstmt, 7, ingredients_tags);} else {pstmt.setString(7, "");}
			if (allergens_tags != null) { setMultiStrings(pstmt, 8, allergens_tags);} else {pstmt.setString(8, "");}
			if (traces_tags != null) { setMultiStrings(pstmt, 9, traces_tags);} else {pstmt.setString(9, "");}
			if (additives_tags != null) { setMultiStrings(pstmt, 10, additives_tags);} else {pstmt.setString(10, "");}
			
			pstmt.addBatch();
			
			//Write batch every n records
			batchCounter++;
			if (batchCounter >= 1000) {
				int[] updateCounts = pstmt.executeBatch();

				//Check results before committing the transaction
				for (int count : updateCounts) {
				    if (count == Statement.EXECUTE_FAILED) {
				        throw new SQLException("Batch insert failed");
				    }
				}
				
				//Commit this batch
				sqlconn.commit();
				batchCounter = 0;
				pstmt.clearBatch();
				batchesWritten++;
				System.out.println(batchesWritten+ " Batches written OK");
			}
			
		} catch (SQLException sqe){
			sqe.printStackTrace();
			return false;		
		} catch (java.lang.ClassCastException cce) {
			cce.printStackTrace();
			return false;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}	//exportDocument
		
	//Set a comma-separated list of tags in a PreparedStatement
	private static void setMultiStrings(PreparedStatement pstmt, int col, List<String> textList) throws SQLException {
	    pstmt.setString(col, String.join(", ", textList));
	}
	
	//Source must be in the format database.collection
	private static boolean validateSource(String source) {
		try {
			//Consider valid if server contains one "." character
			char ch = '.';
			int count = StringUtils.countMatches(source, String.valueOf(ch));
			if (count != 1) {
				return false;
			}
			//Split into database and collection
			sourceDatabase=getLeftString(source, '.');
			sourceCollection=getRightString(source, '.');
			return true;
			
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}	//validateSource
	
	//Destination will be filepath of the database
	private static boolean validateDestination(String destination) {
		try {
			//Check that this is a valid path
			//Does not check whether the path and filename actually exists
		    try {
		        Paths.get(destination);
		    } catch (InvalidPathException ex) {
		        return false;
		    }
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}	//validateDestination
	
	//Get all characters to the left of specified character
	private static String getLeftString(String st, char ch) {
	    int index = st.indexOf(ch);
	    if (index == -1) {
	        return "";	//Char not found. Return empty string
	    }
	    return st.substring(0, index);
	}
	
	//Get all characters to the right of specified character
	private static String getRightString(String st, char ch) {
		int index = st.indexOf(ch);
		if (index == -1) {
		    return ""; //Char not found. Return empty string
		}
		return st.substring(index+1);
	}
	
	//Get the current time as a string in hh:mm:ss format
	private static String getCurrentTime() {
		SimpleDateFormat f = new SimpleDateFormat("hh:mm:ss");
        return f.format(new Date());
	}

}
