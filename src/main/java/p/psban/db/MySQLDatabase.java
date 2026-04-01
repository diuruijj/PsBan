package p.psban.db;

import p.psban.PsBanPlugin;

public class MySQLDatabase extends FlatFileDatabase {
    public MySQLDatabase(PsBanPlugin plugin) {
        super(plugin, "psban-data.yml");
        plugin.getLogger().warning("storage.type=mysql no esta soportado en esta build corregida; se usara almacenamiento local en psban-data.yml");
    }
}
