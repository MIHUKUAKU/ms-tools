// Optional inventory deposit and simple straight-line container routing for ms.sc.
// Kept separate so a failure here cannot prevent the core /ms command tree loading.

__config() -> {
    'scope' -> 'global',
    'stay_loaded' -> true,
    'command_permission' -> 'players',
    'commands' -> {
        '' -> '_help',
        '<online_fake>' -> 'put_here',
        '<online_fake> <saved_box_id_int>' -> 'put_saved',
        'add <box_id_int> <target_player>' -> 'add_box_for_player',
        'addat <box_id_int> <x_float> <y_float> <z_float> <yaw_float> <pitch_float> <put_dimension>' -> 'add_box_at',
        'list' -> 'list_boxes',
        'remove <saved_box_id_int>' -> 'remove_box'
    },
    'arguments' -> {
        'online_fake' -> {
            'type' -> 'term',
            'suggester' -> _(args) -> map(
                filter(player('all'), _~'player_type' == 'fake'),
                _~'name'
            )
        },
        'box_id_int' -> {'type' -> 'int', 'min' -> 1},
        'put_dimension' -> {
            'type' -> 'term',
            'options' -> ['overworld', 'the_nether', 'the_end']
        },
        'saved_box_id_int' -> {
            'type' -> 'int',
            'min' -> 1,
            'suggester' -> _(args) -> keys(parse_nbt(global_boxes))
        },
        'target_player' -> {
            'type' -> 'players',
            'single' -> true
        }
    }
};

global_boxes = read_file('ms_put_boxes', 'shared_nbt');
if(global_boxes == null, global_boxes = nbt('{}'));
global_routes = {};

_help() -> (
    print('PUT: /put <online_fake>');
    print('PUT: /put <online_fake> <box_id>');
    print('PUT: /put add <box_id> <registering_player>');
    print('PUT: /put addat <box_id> <x> <y> <z> <yaw> <pitch> <dimension>');
    print('PUT: /put list | /put remove <box_id>');
    null
);

_short_dimension(entity) -> (
    dimension = str(entity~'dimension');
    if(dimension ~ 'the_nether$', 'the_nether', dimension ~ 'the_end$', 'the_end', 'overworld')
);

_online_fake(bot) -> (
    bot = str(bot);
    fake = player(bot);
    if(fake == null,
        (
            print('MS: player '+bot+' is not online.');
            null
        ),
        if(fake~'player_type' != 'fake',
            (
                print('MS: '+bot+' is online but is not a fake player.');
                null
            ),
            fake
        )
    )
);

_save_boxes() -> write_file('ms_put_boxes', 'shared_nbt', global_boxes);

put_here(bot) -> (
    fake = _online_fake(bot);
    if(fake != null,
        (
            bot = str(fake~'name');
            if(global_routes:bot != null,
                (
                    delete(global_routes, bot);
                    run('player '+bot+' move')
                )
            );
            target = query(fake, 'trace', 5, 'blocks');
            if(target == null || inventory_size(target) == null,
                print('MS: '+bot+' is not looking at a container.'),
                _deposit_and_report(fake, target, 'the container in front')
            )
        )
    );
    null
);

add_box_for_player(id, target_name) -> (
    id = str(id);
    actor = player(str(target_name));
    if(actor == null,
        print('MS: registering player '+target_name+' is not online.'),
        if(global_boxes:id != null,
            print('MS: put box ID '+id+' already exists. Remove it first.'),
            (
                target = query(actor, 'trace', 5, 'blocks');
                if(target == null || inventory_size(target) == null,
                    print('MS: look directly at a chest or other container within 5 blocks, then use /ms put add '+id+'.'),
                    (
                        [cx, cy, cz] = pos(target);
                        [ax, ay, az, ayaw, apitch] = actor~'location';
                        box = nbt('{}');
                        box:'x' = cx;
                        box:'y' = cy;
                        box:'z' = cz;
                        box:'approach_x' = ax;
                        box:'approach_y' = ay;
                        box:'approach_z' = az;
                        box:'yaw' = ayaw;
                        box:'pitch' = apitch;
                        box:'dimension' = _short_dimension(actor);
                        box:'type' = str(target);
                        global_boxes:id = box;
                        if(_save_boxes(),
                            print('MS: saved put box ['+id+'] '+str(target)+' @ '+cx+' '+cy+' '+cz+'; approach point '+str('%.2f %.2f %.2f', ax, ay, az)),
                            (
                                delete(global_boxes, id);
                                print('MS: failed to save put box ['+id+'].')
                            )
                        )
                    )
                )
            )
        )
    );
    null
);

// Registers the same approach point as the player-based form, but obtains the
// target container by ray tracing from the supplied standing position/view.
add_box_at(id, x, y, z, yaw, pitch, dimension) -> (
    id = str(id);
    dimension = str(dimension);
    if(global_boxes:id != null,
        print('MS: put box ID '+id+' already exists. Remove it first.'),
        (
            target = _container_from_view(x, y, z, yaw, pitch, dimension);
            if(target == null,
                print('MS: no container was found within 5 blocks of that position and view.'),
                (
                    [cx, cy, cz] = pos(target);
                    box = nbt('{}');
                    box:'x' = cx;
                    box:'y' = cy;
                    box:'z' = cz;
                    box:'approach_x' = x;
                    box:'approach_y' = y;
                    box:'approach_z' = z;
                    box:'yaw' = yaw;
                    box:'pitch' = pitch;
                    box:'dimension' = dimension;
                    box:'type' = str(target);
                    global_boxes:id = box;
                    if(_save_boxes(),
                        print('MS: saved put box ['+id+'] '+str(target)+' @ '+cx+' '+cy+' '+cz+'; approach point '+str('%.2f %.2f %.2f', x, y, z)+' facing '+yaw+' '+pitch),
                        (
                            delete(global_boxes, id);
                            print('MS: failed to save put box ['+id+'].')
                        )
                    )
                )
            )
        )
    );
    null
);

_container_from_view(x, y, z, yaw, pitch, dimension) -> (
    horizontal = cos(pitch);
    direction_x = -sin(yaw)*horizontal;
    direction_y = -sin(pitch);
    direction_z = cos(yaw)*horizontal;
    eye_y = y+1.62;
    for(range(1, 101),
        (
            ray_distance = _*0.05;
            block_x = floor(x+direction_x*ray_distance);
            block_y = floor(eye_y+direction_y*ray_distance);
            block_z = floor(z+direction_z*ray_distance);
            target = in_dimension(dimension, block(block_x, block_y, block_z));
            if(inventory_size(target) != null, return(target))
        )
    );
    null
);

list_boxes() -> (
    ids = sort(keys(parse_nbt(global_boxes)));
    if(length(ids) == 0,
        print('MS: no put boxes are saved.'),
        (
            print('MS: saved put boxes ('+length(ids)+'):');
            for(ids,
                (
                    box = global_boxes:_;
                    print('['+_+'] '+box:'type'+' @ '+box:'x'+' '+box:'y'+' '+box:'z'+' | approach '+str('%.2f %.2f %.2f', box:'approach_x', box:'approach_y', box:'approach_z')+' | '+box:'dimension')
                )
            )
        )
    );
    null
);

remove_box(id) -> (
    id = str(id);
    box = global_boxes:id;
    if(box == null,
        print('MS: put box ID '+id+' was not found.'),
        (
            delete(global_boxes, id);
            if(_save_boxes(),
                print('MS: removed put box ['+id+'].'),
                (
                    global_boxes:id = box;
                    print('MS: failed to remove put box ['+id+'].')
                )
            )
        )
    );
    null
);

put_saved(bot, id) -> (
    id = str(id);
    box = global_boxes:id;
    fake = _online_fake(bot);
    if(box == null,
        print('MS: put box ID '+id+' was not found.'),
        if(fake != null,
            _start_route(fake, id, box)
        )
    );
    null
);

_start_route(fake, id, box) -> (
    bot = str(fake~'name');
    if(_short_dimension(fake) != str(box:'dimension'),
        print('MS: '+bot+' and put box ['+id+'] are in different dimensions.'),
        (
            target_x = box:'approach_x';
            target_y = box:'approach_y';
            target_z = box:'approach_z';
            dx = target_x - fake~'x';
            dz = target_z - fake~'z';
            distance = sqrt(dx*dx + dz*dz);
            height_difference = abs(target_y - fake~'y');
            if(distance > 100,
                print('MS: put box ['+id+'] is '+str('%.1f', distance)+' blocks away; the limit is 100.'),
                if(height_difference > 0.6,
                    print('MS: route to put box ['+id+'] refused: the saved approach point is not on the same flat level.'),
                    if(distance <= 1.1,
                        _arrive_and_deposit(bot, id, box),
                        if(!_straight_path_clear(fake, box, distance),
                            print('MS: route to put box ['+id+'] refused: the straight flat path has a gap or obstruction.'),
                            (
                                run('player '+bot+' stop');
                                modify(fake, 'yaw', -atan2(dx, dz));
                                modify(fake, 'pitch', 0);
                                token = str(time())+'_'+rand(1000000);
                                global_routes:bot = token;
                                run('player '+bot+' move forward');
                                schedule(1, '_route_tick', bot, id, token, distance, 0, max(200, ceil(distance*15)));
                                print('MS: '+bot+' walking straight to put box ['+id+'] ('+str('%.1f', distance)+' blocks).')
                            )
                        )
                    )
                )
            )
        )
    )
);

_straight_path_clear(fake, box, distance) -> (
    start_x = fake~'x';
    start_y = fake~'y';
    start_z = fake~'z';
    target_x = box:'approach_x';
    target_z = box:'approach_z';
    dimension = str(box:'dimension');
    samples = max(1, ceil(distance*2));
    for(range(samples+1),
        (
            ratio = _/samples;
            sample_x = start_x+(target_x-start_x)*ratio;
            sample_z = start_z+(target_z-start_z)*ratio;
            if(!in_dimension(dimension, _walkable_sample(sample_x, start_y, sample_z)), return(false))
        )
    );
    true
);

_walkable_sample(x, y, z) -> (
    block_x = floor(x);
    block_z = floor(z);
    body_y = ceil(y);
    ground_y = floor(y-0.1);
    blocks_movement(block(block_x, ground_y, block_z)) &&
    !blocks_movement(block(block_x, body_y, block_z)) &&
    !blocks_movement(block(block_x, body_y+1, block_z))
);

_route_tick(bot, id, token, previous_distance, stalled_ticks, ticks_left) -> (
    if(global_routes:bot != token,
        null,
        (
            fake = player(bot);
            box = global_boxes:id;
            if(fake == null || fake~'player_type' != 'fake',
                (
                    delete(global_routes, bot);
                    print('MS: put route cancelled because '+bot+' is offline.')
                ),
                if(box == null,
                    _stop_route(bot, 'put route cancelled because box ['+id+'] was removed.'),
                    (
                        dx = box:'approach_x' - fake~'x';
                        dz = box:'approach_z' - fake~'z';
                        distance = sqrt(dx*dx + dz*dz);
                        height_difference = abs(box:'approach_y' - fake~'y');
                        if(distance <= 1.1,
                            (
                                run('player '+bot+' move');
                                delete(global_routes, bot);
                                _arrive_and_deposit(bot, id, box)
                            ),
                            if(height_difference > 0.8,
                                _stop_route(bot, 'put route stopped: '+bot+' left the saved flat level.'),
                                if(distance > previous_distance+0.15,
                                    _stop_route(bot, 'put route stopped: '+bot+' passed or moved away from box ['+id+'].'),
                                    (
                                        new_stalled_ticks = if(previous_distance-distance < 0.005, stalled_ticks+1, 0);
                                        if(new_stalled_ticks >= 20,
                                            _stop_route(bot, 'put route stopped by an obstacle before box ['+id+'].'),
                                            if(ticks_left <= 0,
                                                _stop_route(bot, 'put route timed out before box ['+id+'].'),
                                                schedule(1, '_route_tick', bot, id, token, distance, new_stalled_ticks, ticks_left-1)
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    );
    null
);

_stop_route(bot, message) -> (
    run('player '+bot+' move');
    delete(global_routes, bot);
    print('MS: '+message);
    null
);

_arrive_and_deposit(bot, id, box) -> (
    fake = player(bot);
    if(fake == null || fake~'player_type' != 'fake',
        print('MS: '+bot+' is no longer online.'),
        (
            modify(fake, 'yaw', box:'yaw');
            modify(fake, 'pitch', box:'pitch');
            target = in_dimension(str(box:'dimension'), block(box:'x', box:'y', box:'z'));
            if(inventory_size(target) == null,
                print('MS: put box ['+id+'] is missing or is no longer a container.'),
                _deposit_and_report(fake, target, 'put box ['+id+']')
            )
        )
    );
    null
);

_deposit_and_report(fake, target, description) -> (
    [moved_items, remaining_items, skipped_shulkers] = _deposit_inventory(fake, target);
    bot = str(fake~'name');
    if(moved_items == 0 && remaining_items == 0,
        print('MS: '+bot+' has no items in the main inventory to put into '+description+'.'),
        if(remaining_items == 0,
            print('MS: '+bot+' put all '+moved_items+' item(s) into '+description+'.'),
            print('MS: '+bot+' put '+moved_items+' item(s) into '+description+'; '+remaining_items+' item(s) remain because the container is full or rejects shulker boxes.')
        )
    );
    if(skipped_shulkers > 0,
        print('MS: left '+skipped_shulkers+' shulker box item(s) with '+bot+' because shulker boxes cannot be nested.')
    );
    null
);

_deposit_inventory(fake, target) -> (
    moved_items = 0;
    remaining_items = 0;
    skipped_shulkers = 0;
    target_is_shulker = _is_shulker(target);
    for(range(36),
        (
            source_slot = _;
            stack = inventory_get(fake, source_slot);
            if(stack != null,
                (
                    if(target_is_shulker && _is_shulker(stack:0),
                        (
                            remaining = stack:1;
                            skipped_shulkers += stack:1
                        ),
                        remaining = _store_in_container(target, stack)
                    );
                    moved_items += stack:1-remaining;
                    remaining_items += remaining;
                    inventory_set(fake, source_slot, remaining)
                )
            )
        )
    );
    [moved_items, remaining_items, skipped_shulkers]
);

_store_in_container(target, stack) -> (
    remaining = stack:1;
    item_name = str(stack:0);
    item_limit = stack_limit(item_name);
    for(range(inventory_size(target)),
        (
            target_slot = _;
            current = inventory_get(target, target_slot);
            if(remaining > 0 && current != null && current:0 == stack:0 && str(current:2) == str(stack:2) && current:1 < item_limit,
                (
                    moved = min(remaining, item_limit-current:1);
                    inventory_set(target, target_slot, current:1+moved);
                    remaining = remaining-moved
                )
            )
        )
    );
    for(range(inventory_size(target)),
        (
            target_slot = _;
            if(remaining > 0 && inventory_get(target, target_slot) == null,
                (
                    moved = min(remaining, item_limit);
                    inventory_set(target, target_slot, moved, item_name, stack:2);
                    remaining = remaining-moved
                )
            )
        )
    );
    remaining
);

_is_shulker(value) -> (
    name = str(value);
    name == 'shulker_box' || name == 'minecraft:shulker_box' || bool(name ~ '_shulker_box$')
);
