load 'deploy'

require 'bundler/capistrano'

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
  when "production" then "master"
  else "staging"
  end
end
set :use_sudo, false

set :hipchat_token, '39019837fa847cfb17282ed5d3fbce'
set :hipchat_room_name, 'bots'
set :hipchat_announce, true

after "deploy", "deploy:cleanup"

namespace :deploy do
  task :start, :roles => :app, :except => { :no_release => true } do
    run "#{sudo} start risingtide"
  end

  task :stop, :roles => :app, :except => { :no_release => true } do
    run "#{sudo} stop risingtide"
  end

  task :restart, :roles => :app, :except => { :no_release => true } do
    stop
    start
  end
end
