load 'deploy'

set :stages, %w(demo staging production)
set :default_stage, "staging"
require 'capistrano/ext/multistage'

set :user, 'utah'
set :application, 'risingtide'
# domain is set in config/deploy/{stage}.rb

# file paths
set :repository, "git@github.com:utahstreetlabs/risingtide.git"
set(:deploy_to) { "/home/#{user}/#{application}" }

# one server plays all roles
role :app do
  fetch(:domain)
end

set :deploy_via, :remote_cache
set :scm, 'git'
set :scm_verbose, true
set(:branch) do
  case stage
  when :production then "production"
  else "staging"
  end
end
set :use_sudo, false

set :hipchat_token, '39019837fa847cfb17282ed5d3fbce'
set :hipchat_room_name, 'bots'
set :hipchat_announce, true

# total number of workers
set(:workers) do
  case stage
  when :production then 16
  else 4
  end
end

# whether or not topology debug mode should be enabled
# see https://github.com/nathanmarz/storm/blob/master/src/jvm/backtype/storm/Config.java
set(:storm_debug) do
  case stage
  when :production then 'false'
  else 'true'
  end
end

after "deploy", "deploy:cleanup"

namespace :deploy do
  task :build_uberjar do
    run "cd #{current_path} && bin/lein clean && bin/lein compile && bin/lein uberjar"
  end
  before "deploy:restart", "deploy:build_uberjar"

  task :start, :roles => :app, :except => { :no_release => true } do
    run "storm/current/bin/storm jar risingtide/current/target/risingtide-*-standalone.jar risingtide.storm.FeedTopology workers #{workers} debug #{storm_debug}"
  end

  task :stop, :roles => :app, :except => { :no_release => true } do
    run "storm/current/bin/storm kill 'feed topology'; true"
  end

  task :restart, :roles => :app, :except => { :no_release => true } do
    stop
    # wait maximum amount of time for topology to stop
    # should match topology.message.timeout.secs in topology config
    sleep 60
    start
  end
end
