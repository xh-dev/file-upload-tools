#!/bin/bash
mongosh mongodb://mongo.example.com:27017 <<EOF
var config = {
    "_id": "dbrs",
    "version": 1,
    "members": [
        {
            "_id": 1,
            "host": "mongo.example.com:27017",
            "priority": 3
        }
    ]
};
rs.initiate(config, { force: true });
rs.status();
