ALTER TABLE `push_tests` ADD `category` text DEFAULT 'test' NOT NULL;--> statement-breakpoint
ALTER TABLE `push_tests` ADD `title` text;--> statement-breakpoint
ALTER TABLE `push_tests` ADD `body` text;--> statement-breakpoint
ALTER TABLE `push_tests` ADD `action_url` text;