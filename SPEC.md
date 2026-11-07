# Technical specification - auction catalog

## 1. Overview

This is a simple application for managing an auction catalog. There are two main use-cases:

- edit the catalog: this requires login
- view the catalog: this is open for all

There shall be two different frontend applications:

- one for everyone, listing catalogs and items in catalog. If there is only one catalog, go straight to this.
- one for logged in users, for modifying the catalog

## 2. data model

The catalog consists of items. Each item has the following properties:

- id (an UUID)
- title
- description
- list of images, the first of the list is the main image
- estimated price range with a lower and upper bound
- starting price
- date added

There is also a catalog which is an ordered list of items. There can be several catalogs and each item may be a part of several catalog.
The catalog has properties:

- id
- name
- date
- visible (true/false)

## 3. backend

API available everyone:

- list visible catalogs
- list items in catalog

API available for logged in users

- list all catalogs
- list all items
- create new item
- patch/update existing item
- add item to catalog
- remove item from catalog
- delete item
- delete all items

The API-s should be REST-like JSON-endpoints. Images shall be stored in S3. Other data shall be stored in an SQLite database, but the database integration should be encapsulated in a way which enables this to be changed to PostgreSQL or another database later.

For the first version, use basic-auth for authentication with a hard-coded password.

Describe the API using an Open-API spec and make it testable with swagger.

## 4. frontend for viewing the catalog, open to all users

Showing a catalog shows a list of items with:

- title
- description
- price range
- starting price
- a small version of the main image

For showing a specific item, open an overlay div showing the same info, but with larger images, and letting the user browse through images.

Make sure that this works both on a phone and on a computer.

## 5. frontend for editing

Feature: add or modify catalog

- change name, data and visibility
- change order of items in catalog
- remove items from catalog
- add items to catalog

Feature: add or modify item

- upload images, must handle drag-and-drop and uploading several images at once
- edit title, description, price range and starting price
- add or remove item from any catalog

Must work from phone or computer, but phone is second priority

