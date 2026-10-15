import {sqliteTable,text,integer,index} from 'drizzle-orm/sqlite-core';
export const pushDevices=sqliteTable('push_devices',{
 tokenHash:text('token_hash').primaryKey(), userId:text('user_id').notNull(),
 tokenCipher:text('token_cipher').notNull(), updatedAt:integer('updated_at').notNull(),
},t=>[index('push_devices_user').on(t.userId)]);
export const pushTests=sqliteTable('push_tests',{
 id:text('id').primaryKey(),userId:text('user_id').notNull(),tokenHash:text('token_hash').notNull(),
 receiptHash:text('receipt_hash').notNull(),createdAt:integer('created_at').notNull(),
 state:text('state').notNull(),receivedAt:integer('received_at'),
 eventId:text('event_id'),failureCode:text('failure_code'),category:text('category').notNull().default('test'),title:text('title'),body:text('body'),actionUrl:text('action_url'),
},t=>[index('push_tests_user').on(t.userId)]);
export const businessNotifications=sqliteTable('business_notifications',{
 id:text('id').primaryKey(),userId:text('user_id').notNull(),category:text('category').notNull(),
 title:text('title').notNull(),body:text('body').notNull(),actionUrl:text('action_url').notNull(),
 state:text('state').notNull(),createdAt:integer('created_at').notNull(),receivedAt:integer('received_at'),
},t=>[index('business_notifications_user_created').on(t.userId,t.createdAt)]);
