#!/bin/bash
if grep DB_PASSWORD .env &>/dev/null &
then
    password="$(grep DB_PASSWORD .env | cut -d "=" -f2)"
else
    password='pass'
fi

PGPASSWORD=$password psql -U postgres << EOF
DROP DATABASE history;
EOF