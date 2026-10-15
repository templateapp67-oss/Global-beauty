CREATE TABLE `business_notifications` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`category` text NOT NULL,
	`title` text NOT NULL,
	`body` text NOT NULL,
	`action_url` text NOT NULL,
	`state` text NOT NULL,
	`created_at` integer NOT NULL,
	`received_at` integer
);
--> statement-breakpoint
CREATE INDEX `business_notifications_user_created` ON `business_notifications` (`user_id`,`created_at`);--> statement-breakpoint
ALTER TABLE `push_tests` ADD `event_id` text;--> statement-breakpoint
ALTER TABLE `push_tests` ADD `failure_code` text;