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
  "storm"
  # case stage
  # when "production" then "master"
  # else "storm"
  # end
end
set :use_sudo, false

set :hipchat_token, '39019837fa847cfb17282ed5d3fbce'
set :hipchat_room_name, 'bots'
set :hipchat_announce, true

# total number of workers
set(:workers) do
  case stage
  when "production" then 12
  else 4
  end
end

after "deploy", "deploy:cleanup"

namespace :deploy do
  task :build_uberjar do
    run "cd #{current_path} && bin/lein clean && bin/lein compile && bin/lein uberjar"
  end
  before "deploy:restart", "deploy:build_uberjar"

  task :start, :roles => :app, :except => { :no_release => true } do
    run "storm/current/bin/storm jar risingtide/current/target/risingtide-*-standalone.jar risingtide.storm.FeedTopology debug true workers #{workers}"
  end

  task :stop, :roles => :app, :except => { :no_release => true } do
    run "storm/current/bin/storm kill 'feed topology'; true"
  end

  task :restart, :roles => :app, :except => { :no_release => true } do
    stop
    # wait maximum amount of time for topology to stop
    # should match topology.message.timeout.secs in storm.yaml
    sleep 30
    start
  end
end
