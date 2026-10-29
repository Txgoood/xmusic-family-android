-- PostgreSQL Şeması

CREATE TABLE families (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE profiles (
    id SERIAL PRIMARY KEY,
    family_id INTEGER REFERENCES families(id),
    name VARCHAR(255) NOT NULL,
    avatar_url TEXT,
    pin_hash VARCHAR(255)
);

CREATE TABLE favorites (
    id SERIAL PRIMARY KEY,
    profile_id INTEGER REFERENCES profiles(id),
    track_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
