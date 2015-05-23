# users

# --- !Ups

CREATE TABLE `users` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `username` varchar(50) DEFAULT NULL,
  `password` varchar(100) DEFAULT NULL,
  `userid` bigint(20) unsigned DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `screen_name` varchar(100) NOT NULL,
  `token` varchar(100) DEFAULT NULL,
  `secret` varchar(100) DEFAULT NULL,
  `created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modified` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `screen_name` (`screen_name`),
  UNIQUE KEY `userid` (`userid`)
) ENGINE=InnoDB;

# --- !Downs

DROP TABLE `users`;