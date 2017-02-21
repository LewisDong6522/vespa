// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.metrics.job_tracker");
#include "job_tracker.h"
#include <vespa/fastos/timestamp.h>
#include <chrono>

using fastos::TimeStamp;
using fastos::ClockSystem;

namespace proton {

JobTracker::JobTracker(std::chrono::time_point<std::chrono::steady_clock> now, std::mutex &lock)
    : _sampler(now),
      _lock(lock)
{
}

double
JobTracker::sampleLoad(std::chrono::time_point<std::chrono::steady_clock> now, const std::lock_guard<std::mutex> &guard)
{
    (void) guard;
    return _sampler.sampleLoad(now);
}

void
JobTracker::start()
{
    std::unique_lock<std::mutex> guard(_lock);
    _sampler.startJob(std::chrono::steady_clock::now());
}

void
JobTracker::end()
{
    std::unique_lock<std::mutex> guard(_lock);
    _sampler.endJob(std::chrono::steady_clock::now());
}

} // namespace proton
