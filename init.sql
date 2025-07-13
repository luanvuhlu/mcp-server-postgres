-- Sample initialization script for testing PostgreSQL MCP server
-- This script creates some sample schemas and tables for testing

-- Create sample schemas
CREATE SCHEMA IF NOT EXISTS sales;
CREATE SCHEMA IF NOT EXISTS inventory;

-- Create tables in public schema
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS products (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create tables in sales schema
CREATE TABLE IF NOT EXISTS sales.orders (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id),
    total_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sales.order_items (
    id SERIAL PRIMARY KEY,
    order_id INTEGER REFERENCES sales.orders(id),
    product_id INTEGER REFERENCES products(id),
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL
);

-- Create tables in inventory schema
CREATE TABLE IF NOT EXISTS inventory.stock (
    id SERIAL PRIMARY KEY,
    product_id INTEGER REFERENCES products(id),
    quantity INTEGER NOT NULL,
    warehouse_location VARCHAR(50),
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert sample data
INSERT INTO users (username, email) VALUES 
    ('john_doe', 'john@example.com'),
    ('jane_smith', 'jane@example.com'),
    ('bob_wilson', 'bob@example.com');

INSERT INTO products (name, price, description) VALUES 
    ('Laptop Computer', 999.99, 'High-performance laptop for work and gaming'),
    ('Wireless Mouse', 29.99, 'Ergonomic wireless mouse with long battery life'),
    ('Mechanical Keyboard', 89.99, 'RGB mechanical keyboard with blue switches');

INSERT INTO sales.orders (user_id, total_amount, status) VALUES 
    (1, 1119.97, 'completed'),
    (2, 29.99, 'pending'),
    (3, 999.99, 'shipped');

INSERT INTO sales.order_items (order_id, product_id, quantity, unit_price) VALUES 
    (1, 1, 1, 999.99),
    (1, 2, 1, 29.99),
    (1, 3, 1, 89.99),
    (2, 2, 1, 29.99),
    (3, 1, 1, 999.99);

INSERT INTO inventory.stock (product_id, quantity, warehouse_location) VALUES 
    (1, 25, 'Warehouse A'),
    (2, 150, 'Warehouse B'),
    (3, 75, 'Warehouse A');

-- Add comments to tables for documentation
COMMENT ON TABLE users IS 'User accounts and profile information';
COMMENT ON TABLE products IS 'Product catalog with pricing information';
COMMENT ON TABLE sales.orders IS 'Customer orders and transaction records';
COMMENT ON TABLE sales.order_items IS 'Individual items within each order';
COMMENT ON TABLE inventory.stock IS 'Current stock levels and warehouse locations';
