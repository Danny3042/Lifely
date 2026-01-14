#!/usr/bin/env ruby
# Adds a set of Swift source files to the iOS app Xcode project and attaches them to the main app target.
# Usage:
#   gem install xcodeproj
#   ruby add_swift_files_to_xcode.rb

require 'xcodeproj'
require 'pathname'

# CONFIG: path to the .xcodeproj file (relative to this script)
PROJECT_PATH = 'iosApp.xcodeproj'

# The list of files (relative to the project folder) you want to ensure are part of the app target
SWIFT_FILES = [
  'ContentView.swift',
  'ContentViewImpl.swift',
  'ComposeBridge.swift',
  'SharedComposeHost.swift',
  'ComposeViewController.swift',
  'Helpers.swift',
  'ChartSupport.swift',
  'LoginView.swift',
  'HeroTabView.swift',
  'HabitTrackerView.swift',
  'ChatView.swift',
  'MeditationView.swift',
  'ProfileView.swift',
  'StressManagementView.swift',
  'AppSettings.swift',
  # Added missing sign-up / reset views
  'SignUpView.swift',
  'ResetPasswordView.swift',
  'AuthManager.swift'
]

project_dir = Pathname.new(File.dirname(PROJECT_PATH))
project_file = Pathname.new(PROJECT_PATH)

unless project_file.exist?
  puts "Error: Xcode project not found at #{PROJECT_PATH}"
  exit 1
end

proj = Xcodeproj::Project.open(PROJECT_PATH)

# Choose the primary app target: try to find the one with 'app' product type or the first target
app_target = proj.targets.find do |t|
  t.product_type == 'com.apple.product-type.application'
end
app_target ||= proj.targets.first

if app_target.nil?
  puts "Error: No target found in project"
  exit 1
end

puts "Using target: #{app_target.name}"

# Find or create a Sources group at project root (same folder as existing ContentView.swift)
main_group = proj.main_group
sources_group = nil

# Prefer existing group that contains ContentView.swift
proj.main_group.groups.each do |g|
  if g.files.any? { |f| f.path == 'ContentView.swift' }
    sources_group = g
    break
  end
end

# fallback to main group
sources_group ||= main_group

added = []
SKIPPED = []

SWIFT_FILES.each do |rel_path|
  file_path_on_disk = project_dir.join('iosApp').join(rel_path)
  # if file exists inside project folder iosApp/iosApp
  if !file_path_on_disk.exist?
    # try project_dir (project root same folder as xcodeproj contains files)
    alt_path = project_dir.join(rel_path)
    if alt_path.exist?
      file_path_on_disk = alt_path
    else
      puts "Warning: source file not found on disk: #{rel_path} (skipping)"
      SKIPPED << rel_path
      next
    end
  end

  # check if project already has a file reference for this path
  existing = proj.files.find { |f| f.path == rel_path || f.path == "iosApp/#{rel_path}" }
  if existing
    puts "Already referenced in project: #{rel_path}"
    # ensure it's added to the target build phase
    unless app_target.source_build_phase.files_references.include?(existing)
      app_target.add_file_references([existing])
      puts "  -> added existing file reference to target #{app_target.name}"
    end
    next
  end

  # create file reference in the chosen group
  file_ref = sources_group.new_file(file_path_on_disk.to_s)
  if file_ref
    app_target.add_file_references([file_ref])
    added << rel_path
    puts "Added and attached to target: #{rel_path}"
  else
    puts "Failed to add file reference for #{rel_path}"
  end
end

# Save project
proj.save

puts "\nSummary:\n  Added: #{added.count} files\n  Skipped (not found): #{SKIPPED.count} files\n"
puts "If you still see " + '"file does not belong to any project target" warnings in Xcode, open the project and confirm target membership for the files.'
puts "Done."
