# -*- encoding: utf-8 -*-
require File.expand_path('../lib/rising_tide/version', __FILE__)

Gem::Specification.new do |s|
  s.name = 'risingtide'
  s.version = RisingTide::VERSION.dup
  s.required_rubygems_version = Gem::Requirement.new(">= 1.3.6") if s.respond_to? :required_rubygems_version=
  s.required_ruby_version = Gem::Requirement.new(">= 1.9.2")
  s.authors = ['Brian Moseley', 'Robert Zuber']
  s.description = 'RisingTide stories service client'
  s.email = ['bcm@copious.com']
  s.homepage = 'http://github.com/utahstreetlabs/risingtide'
  s.rdoc_options = ['--charset=UTF-8']
  s.summary = "A client library for the RisingTide stories service"
  s.executables = `git ls-files -- bin/*`.split("\n").map{ |f| File.basename(f) }
  s.files = `git ls-files -- lib/*`.split("\n")
  s.test_files = `git ls-files -- {test,spec,features}/*`.split("\n")

  s.add_development_dependency('rake')
  s.add_development_dependency('mocha')
  s.add_development_dependency('rspec')
  s.add_development_dependency('gemfury')
  s.add_runtime_dependency('kaminari', ['~> 0.13.0'])
  s.add_runtime_dependency('ladon')
  s.add_runtime_dependency('redis')
  s.add_runtime_dependency('yajl-ruby')
  s.add_runtime_dependency('riak-client')
end
