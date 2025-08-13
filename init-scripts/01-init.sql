#
!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- Create additional configuration if needed
    CREATE
EXTENSION IF NOT EXISTS "uuid-ossp";
    
    -- Grant privileges
    GRANT ALL PRIVILEGES ON DATABASE
member_management_db TO postgres;
    
    -- Create indexes for better performance (will be created by Hibernate, but good to have)
    -- These will be created automatically by JPA, but listing here for reference

\echo
'Database initialization completed successfully'
EOSQL
