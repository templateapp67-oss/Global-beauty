CREATE TABLE `push_devices` (
	`token_hash` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`token_cipher` text NOT NULL,
	`updated_at` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `push_devices_user` ON `push_devices` (`user_id`);--> statement-breakpoint
CREATE TABLE `push_tests` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`token_hash` text NOT NULL,
	`receipt_hash` text NOT NULL,
	`created_at` integer NOT NULL,
	`state` text NOT NULL,
	`received_at` integer
);
--> statement-breakpoint
CREATE INDEX `push_tests_user` ON `push_tests` (`user_id`);