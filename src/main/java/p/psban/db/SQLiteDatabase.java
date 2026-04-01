package p.psban.db;

import p.psban.PsBanPlugin;

public class SQLiteDatabase extends FlatFileDatabase {
    public SQLiteDatabase(PsBanPlugin plugin) {
        super(plugin, "psban-data.yml");
    }
}
