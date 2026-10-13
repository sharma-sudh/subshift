## Database Backup and Restore

These commands back up and restore the `subshift_db` PostgreSQL database using `pg_dump` and `pg_restore`. Run them from a terminal with PostgreSQL's `bin` folder on your PATH. Each command asks for your Postgres password.

### Back up

```bash
pg_dump -U postgres -h localhost -d subshift_db -Fc -f subshift_backup.dump
```

| Option | Meaning |
|---|---|
| `-U postgres` | Connect as the `postgres` user |
| `-h localhost` | Database host |
| `-d subshift_db` | Database to back up |
| `-Fc` | Custom compressed format, which is the format `pg_restore` needs |
| `-f subshift_backup.dump` | Output file |

Use `-f` to name the output file instead of redirecting with `>`. In Windows PowerShell, `>` can corrupt the backup file.

### Restore into a new database

Create an empty database, then restore the backup into it:

```bash
psql -U postgres -h localhost -c "CREATE DATABASE subshift_test;"
pg_restore -U postgres -h localhost -d subshift_test subshift_backup.dump
```

To restore over the original `subshift_db`, replace everything in it with the backup:

```bash
pg_restore -U postgres -h localhost -d subshift_db --clean --if-exists subshift_backup.dump
```

`--clean --if-exists` drops existing objects before recreating them, so **any data added since the backup is lost**.

### Verify the restore

```bash
psql -U postgres -h localhost -d subshift_test -c "\dt"
psql -U postgres -h localhost -d subshift_test -c "SELECT COUNT(*) FROM users;"
```

You should see all the tables and the row counts you expect. To list what a backup file contains without restoring it, run `pg_restore --list subshift_backup.dump`.

### Notes

- The backup contains real user data, including password hashes and encrypted Gmail tokens. **Never commit `.dump` files to Git**, and store them somewhere private.
- The backup includes the schema, the view, the function and the trigger, so you don't need to run `db/schema.sql` again after restoring.
- `pg_restore` can't read plain `.sql` files. For a plain SQL file, use `psql -d <database> -f <file>.sql` instead.
