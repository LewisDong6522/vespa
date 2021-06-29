// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <cstdlib>
#include <cstdio>
#include <pthread.h>
#include <dlfcn.h>
#include <cassert>
#include <malloc.h>

void * run(void * arg)
{
    (void) arg;
    char * a = new char [100]; // a should not remain in stacktrace
    char * b = new char [1];   // but b should as it not deleted.
    (void) b;
    delete [] a;
    return nullptr;
}

void verify_that_vespamalloc_datasegment_size_exists() {
#pragma GCC diagnostic push
#if __GNUC_PREREQ(2, 33)
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
#endif
    struct mallinfo info = mallinfo();
#pragma GCC diagnostic push
    printf("Malloc used %dm of memory\n",info.arena);
    assert(info.arena >= 10);
    assert(info.arena < 10000);
    assert(info.fordblks == 0);
    assert(info.fsmblks == 0);
    assert(info.hblkhd == 0);
    assert(info.hblks == 0);
    assert(info.keepcost == 0);
    assert(info.ordblks == 0);
    assert(info.smblks == 0);
    assert(info.uordblks == 0);
    assert(info.usmblks == 0);
}

int main(int argc, char *argv[])
{
    (void) argc;
    (void) argv;
    char * a = new char [100]; // a should not remain in stacktrace
    char * b = new char [1];   // but b should as it not deleted.
    (void) b;
    delete [] a;
    pthread_t tid;
    int retval = pthread_create(&tid, nullptr, run, nullptr);
    if (retval != 0) {
        perror("pthread_create failed");
        abort();
    }
    retval = pthread_join(tid, nullptr);
    if (retval != 0) {
        perror("pthread_join failed");
        abort();
    }

    verify_that_vespamalloc_datasegment_size_exists();
}
