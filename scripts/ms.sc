// Mob-switch fake-player presets for Fabric Carpet.
// Commands: /ms add, /ms run, /ms walk, /ms keep, /ms fight, /ms drink, /ms create, /ms send, /ms fetch, /ms fetchall, /ms put, /ms craftbox, /ms throw, /ms trade, /ms clear, /ms control, /ms phantom, /ms hardbreak, /ms bots, /ms list, /ms remove

__config() -> {
    'scope' -> 'global',
    'stay_loaded' -> true,
    // This world is played without operator/cheat permission.
    // Expose /ms to players; commands run by the app still use Carpet's script authority.
    'command_permission' -> 'players',
    'commands' -> {
        '' -> '_help',
        'add <id_int> <bot_term> <x_float> <y_float> <z_float> <yaw_float> <pitch_float> <dimension>' -> 'add',
        'run <saved_id_int>' -> 'run_preset',
        'keep <saved_id_int>' -> 'keep_preset',
        'walk <saved_id_int>' -> 'walk_preset',
        'fight <saved_id_int>' -> ['fight_preset', 14],
        'fight <saved_id_int> <attack_interval_int>' -> 'fight_preset',
        'drink <saved_id_int>' -> 'drink_preset',
        'create <bot_term>' -> 'create_here',
        'create <bot_term> <target_player>' -> 'create_at_player',
        'send <online_fake>' -> 'send_here',
        'send <online_fake> <target_player>' -> 'send_to_player',
        'fetch <online_fake>' -> 'fetch_here',
        'fetch <online_fake> <target_player>' -> 'fetch_to_player',
        'put <online_fake>' -> 'put_here',
        'put <online_fake> <put_saved_id_int>' -> 'put_saved',
        'put add <put_id_int>' -> 'put_add',
        'put add <put_id_int> <x_float> <y_float> <z_float> <yaw_float> <pitch_float>' -> 'put_add_at_current_dimension',
        'put add <put_id_int> <x_float> <y_float> <z_float> <yaw_float> <pitch_float> <dimension>' -> 'put_add_at_dimension',
        'put list' -> 'put_list',
        'put remove <put_saved_id_int>' -> 'put_remove',
        'craftbox <online_fake>' -> 'craftbox_self',
        'craftbox <online_fake> <craftbox_source_id_int> <craftbox_output_id_int>' -> 'craftbox_saved',
        'throw <online_fake>' -> 'throw_all',
        'trade <online_fake>' -> 'trade_fake',
        'trade <online_fake> <trade_chain_id_int>' -> 'trade_chain_run',
        'trade chain add <trade_chain_id_int>' -> 'trade_chain_add',
        'trade chain remove <trade_chain_id_int>' -> 'trade_chain_remove',
        'trade chain capture <trade_chain_id_int>' -> ['trade_chain_capture', 32],
        'trade chain capture <trade_chain_id_int> <trade_chain_radius_int>' -> 'trade_chain_capture',
        'trade chain list' -> 'trade_chain_list_all',
        'trade chain list <trade_chain_id_int>' -> 'trade_chain_list_one',
        'trade chain delete <trade_chain_id_int>' -> 'trade_chain_delete',
        'fetchall <control_target>' -> 'fetchall_current_here',
        'fetchall <control_target> <target_player>' -> 'fetchall_current_to_player',
        'fetchall <control_target> l <fetchall_distance_int>' -> 'fetchall_left_here',
        'fetchall <control_target> l <fetchall_distance_int> <target_player>' -> 'fetchall_left_to_player',
        'fetchall <control_target> r <fetchall_distance_int>' -> 'fetchall_right_here',
        'fetchall <control_target> r <fetchall_distance_int> <target_player>' -> 'fetchall_right_to_player',
        'clear <online_fake>' -> 'clear_fake_inventory',
        'control <control_target> move forward' -> ['control_move_forward', 1],
        'control <control_target> move forward <control_distance_float>' -> 'control_move_forward',
        'control <control_target> move backward' -> ['control_move_backward', 1],
        'control <control_target> move backward <control_distance_float>' -> 'control_move_backward',
        'control <control_target> move left' -> ['control_move_left', 1],
        'control <control_target> move left <control_distance_float>' -> 'control_move_left',
        'control <control_target> move right' -> ['control_move_right', 1],
        'control <control_target> move right <control_distance_float>' -> 'control_move_right',
        'control <control_target> move stop' -> 'control_move_stop',
        'control <control_target> look up' -> 'control_look_up',
        'control <control_target> look down' -> 'control_look_down',
        'control <control_target> look north' -> 'control_look_north',
        'control <control_target> look south' -> 'control_look_south',
        'control <control_target> look east' -> 'control_look_east',
        'control <control_target> look west' -> 'control_look_west',
        'control <control_target> look <control_yaw_float> <control_pitch_float>' -> 'control_look',
        'control <control_target> turn left' -> 'control_turn_left',
        'control <control_target> turn right' -> 'control_turn_right',
        'control <control_target> turn back' -> 'control_turn_back',
        'control <control_target> stop' -> 'control_stop',
        'phantom' -> 'phantom_status',
        'phantom status' -> 'phantom_status',
        'phantom off' -> 'phantom_off',
        'phantom on' -> 'phantom_on',
        'hardbreak' -> 'unbreakable_status',
        'hardbreak status' -> 'unbreakable_status',
        'hardbreak off' -> 'unbreakable_off',
        'hardbreak on' -> 'unbreakable_on',
        'bots' -> 'list_bots',
        'list' -> 'list_presets',
        'list <saved_id_int>' -> 'show_preset',
        'remove <saved_id_int>' -> 'remove_preset'
    },
    'arguments' -> {
        'id_int' -> {'type' -> 'int', 'min' -> 1},
        'saved_id_int' -> {
            'type' -> 'int',
            'min' -> 1,
            'suggester' -> _(args) -> keys(parse_nbt(global_presets))
        },
        'bot_term' -> {'type' -> 'term'},
        'attack_interval_int' -> {
            'type' -> 'int',
            'min' -> 1,
            'suggest' -> [14]
        },
        'target_player' -> {
            'type' -> 'players',
            'single' -> true
        },
        'online_fake' -> {
            'type' -> 'term',
            'suggester' -> _(args) -> map(
                filter(player('all'), _~'player_type' == 'fake'),
                _~'name'
            )
        },
        'control_target' -> {
            'type' -> 'term',
            'suggester' -> _(args) -> (
                suggestions = keys(parse_nbt(global_presets));
                for(
                    filter(player('all'), _~'player_type' == 'fake'),
                    put(suggestions, null, _~'name')
                );
                suggestions
            )
        },
        // Dynamic suggestions run with the command sender as their context.
        // Press Tab on each field to insert the sender's current position/rotation.
        'x_float' -> {
            'type' -> 'float',
            'suggester' -> _(args) -> [str('%.3f', player()~'x')]
        },
        'y_float' -> {
            'type' -> 'float',
            'suggester' -> _(args) -> [str('%.3f', player()~'y')]
        },
        'z_float' -> {
            'type' -> 'float',
            'suggester' -> _(args) -> [str('%.3f', player()~'z')]
        },
        'yaw_float' -> {
            'type' -> 'float',
            'suggester' -> _(args) -> [str('%.2f', player()~'yaw')]
        },
        'control_yaw_float' -> {
            'type' -> 'float',
            'min' -> -180,
            'max' -> 180,
            'suggester' -> _(args) -> _control_yaw_suggestions(args)
        },
        'control_distance_float' -> {
            'type' -> 'float',
            'min' -> 0.1,
            'suggest' -> [1, 2, 5, 10]
        },
        'fetchall_distance_int' -> {
            'type' -> 'int',
            'min' -> 0,
            'suggest' -> [1, 2, 3, 4]
        },
        'put_id_int' -> {'type' -> 'int', 'min' -> 1},
        'put_saved_id_int' -> {
            'type' -> 'int',
            'min' -> 1,
            'suggester' -> _(args) -> _put_box_ids()
        },
        'craftbox_source_id_int' -> {
            'type' -> 'int',
            'min' -> 1,
            'suggester' -> _(args) -> _put_box_ids()
        },
        'craftbox_output_id_int' -> {
            'type' -> 'int',
            'min' -> 1,
            'suggester' -> _(args) -> _put_box_ids()
        },
        'trade_chain_id_int' -> {
            'type' -> 'int',
            'suggester' -> _(args) -> [1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6, 7, -7, 8, -8, 9, -9, 10, -10, 11, -11, 12, -12, 13, -13, 14, -14, 15, -15, 16, -16]
        },
        'trade_chain_radius_int' -> {
            'type' -> 'int',
            'min' -> 1,
            'max' -> 64,
            'suggest' -> [32, 20, 16]
        },
        'control_pitch_float' -> {
            'type' -> 'float',
            'min' -> -90,
            'max' -> 90,
            'suggest' -> [60, 0, -45, -75]
        },
        'pitch_float' -> {
            'type' -> 'float',
            'suggester' -> _(args) -> [str('%.2f', player()~'pitch')]
        },
        'dimension' -> {
            'type' -> 'term',
            'options' -> ['overworld', 'the_nether', 'the_end']
        }
    }
};

global_presets = load_app_data();
if(global_presets == null, global_presets = nbt('{}'));
global_pending_clicks = {};
global_control_moves = {};
global_fetchall = {};
global_fetchall_returns = {};
global_phantom_request = null;
// Per-player and intentionally not persisted: reopening the world is always safe/off.
global_unbreakable_breakers = {};

_help() -> (
    print('MS: /ms add <id> <bot> <x> <y> <z> <yaw> <pitch> <dimension>');
    print('MS: /ms run <id> | /ms walk <id> | /ms keep <id>');
    print('MS: /ms fight <id> [interval_gt=14]');
    print('MS: /ms drink <id>');
    print('MS: /ms create <bot> [target_player]');
    print('MS: /ms send <online_fake> [target_player]');
    print('MS: /ms fetch <online_fake> [target_player]');
    print('MS: /ms put <online_fake> [box_id]');
    print('MS: /ms put add <box_id> [x y z yaw pitch [dimension]]');
    print('MS: /ms put list | /ms put remove <box_id>');
    print('MS: /ms craftbox <online_fake> [material_box_id product_box_id]');
    print('MS: /ms throw <online_fake>');
    print('MS: /ms trade <online_fake> [chain_id] (negative=entity, positive=location)');
    print('MS: /ms trade chain add|remove|capture|list|delete <chain_id>');
    print('MS: /ms fetchall <online_fake|preset_id> [target_player]');
    print('MS: /ms fetchall <online_fake|preset_id> <l|r> <distance> [target_player]');
    print('MS: /ms clear <online_fake>');
    print('MS: /ms control <online_fake> move|look|turn|stop ...');
    print('MS: /ms phantom [status|off|on]');
    print('MS: /ms hardbreak [status|off|on] (ON: left-click bedrock/end portal frame to remove)');
    print('MS: /ms bots');
    print('MS: /ms list [id] | /ms remove <id>');
    null
);

_phantom_spawning_enabled() -> (
    rules = system_info('world_gamerules');
    bool(rules:'minecraft:spawn_phantoms')
);

phantom_status() -> (
    enabled = _phantom_spawning_enabled();
    print('MS: phantom natural spawning is '+if(enabled, 'ON (enabled)', 'OFF (disabled)'));
    null
);

phantom_off() -> _request_phantom_spawning(false);
phantom_on() -> _request_phantom_spawning(true);

unbreakable_status() -> (
    name = str(player()~'name');
    enabled = global_unbreakable_breakers:name == true;
    print('MS: unbreakable breaking for '+name+' is '+if(enabled, 'ON', 'OFF'));
    null
);

unbreakable_on() -> (
    name = str(player()~'name');
    global_unbreakable_breakers:name = true;
    print('MS: unbreakable breaking is ON for '+name+'. Left-click bedrock or an end portal frame to remove it; no item will drop.');
    null
);

unbreakable_off() -> (
    name = str(player()~'name');
    delete(global_unbreakable_breakers, name);
    print('MS: unbreakable breaking is OFF for '+name);
    null
);

__on_player_clicks_block(breaker, block, face) -> (
    name = str(breaker~'name');
    target = str(block);
    if(
        global_unbreakable_breakers:name == true &&
        (block == 'bedrock' || block == 'end_portal_frame'),
        (
            where = pos(block);
            changed = set(block, 'air');
            if(changed,
                print(breaker, 'MS: removed '+target+' @ '+where),
                print(breaker, 'MS: failed to remove '+target+' @ '+where)
            );
            'cancel'
        )
    )
);

_request_phantom_spawning(enabled) -> (
    global_phantom_request = {
        'enabled' -> enabled,
        'requester' -> str(player()~'name')
    };
    print('MS: phantom spawning change queued...');
    null
);

// App commands inherit the survival player's permission level. A server tick
// event runs with Carpet's server script authority, so the gamerule can be
// changed without granting the player OP.
__on_tick() -> (
    if(global_phantom_request != null,
        (
            request = global_phantom_request;
            global_phantom_request = null;
            enabled = request:'enabled';
            requester = player(request:'requester');
            run_result = run('gamerule minecraft:spawn_phantoms '+if(enabled, 'true', 'false'));
            current = _phantom_spawning_enabled();
            message = if(current == enabled,
                'MS: phantom natural spawning is now '+if(current, 'ON (enabled)', 'OFF (disabled)'),
                'MS: failed to change phantom spawning; current state is '+if(current, 'ON (enabled)', 'OFF (disabled)')
            );
            if(requester != null, print(requester, message), print(message))
        )
    );
    null
);

_online_fake_or_error(target) -> (
    fake = player(str(target));
    if(fake == null,
        (
            preset = _find(target);
            if(preset != null, fake = player(str(preset:'bot')))
        )
    );
    if(fake == null,
        (
            print('MS: fake player or preset '+target+' is not online.');
            null
        ),
        if(fake~'player_type' != 'fake',
            (
                print('MS: '+target+' is a real player; control is refused.');
                null
            ),
            fake
        )
    )
);

control_move_forward(target, distance) -> _control_move(target, 'forward', distance);
control_move_backward(target, distance) -> _control_move(target, 'backward', distance);
control_move_left(target, distance) -> _control_move(target, 'left', distance);
control_move_right(target, distance) -> _control_move(target, 'right', distance);

_control_move(target, direction, distance) -> (
    fake = _online_fake_or_error(target);
    if(fake != null,
        (
            bot = fake~'name';
            if(global_fetchall:str(bot) != null,
                (
                    delete(global_fetchall, str(bot));
                    delete(global_fetchall_returns, str(bot));
                    print('MS: cancelled active fetchall for '+bot)
                )
            );
            token = str(time())+'_'+rand(1000000);
            global_control_moves:str(bot) = token;
            start_x = fake~'x';
            start_z = fake~'z';
            run('player '+bot+' move '+direction);
            schedule(1, '_control_move_tick', str(bot), direction, distance, start_x, start_z, token, max(100, distance*100));
            print('MS: '+bot+' moving '+direction+' for '+distance+' block(s)')
        )
    );
    null
);

_control_status(fake) -> str(
    'pos=(%.3f, %.3f, %.3f) yaw=%.2f pitch=%.2f',
    fake~'x', fake~'y', fake~'z', fake~'yaw', fake~'pitch'
);

_control_move_tick(bot, direction, distance, start_x, start_z, token, ticks_left) -> (
    fake = player(bot);
    if(global_control_moves:bot != token,
        null,
        if(fake == null || fake~'player_type' != 'fake',
            delete(global_control_moves, bot),
            (
                dx = fake~'x' - start_x;
                dz = fake~'z' - start_z;
                travelled = sqrt(dx*dx + dz*dz);
                if(travelled >= distance,
                    (
                        run('player '+bot+' move');
                        delete(global_control_moves, bot);
                        print('MS: '+bot+' moved '+str('%.2f', travelled)+' block(s) '+direction+'; stopped | '+_control_status(fake))
                    ),
                    if(ticks_left <= 0,
                        (
                            run('player '+bot+' move');
                            delete(global_control_moves, bot);
                            print('MS: '+bot+' movement stopped early at '+str('%.2f', travelled)+'/'+distance+' block(s) | '+_control_status(fake))
                        ),
                        schedule(1, '_control_move_tick', bot, direction, distance, start_x, start_z, token, ticks_left-1)
                    )
                )
            )
        )
    )
);

control_move_stop(target) -> (
    fake = _online_fake_or_error(target);
    if(fake != null,
        (
            bot = fake~'name';
            delete(global_control_moves, str(bot));
            run('player '+bot+' move');
            print('MS: stopped movement for '+bot+' | '+_control_status(fake))
        )
    );
    null
);

control_look(target, yaw, pitch) -> (
    fake = _online_fake_or_error(target);
    if(fake != null,
        (
            bot = fake~'name';
            modify(fake, 'yaw', yaw);
            modify(fake, 'pitch', pitch);
            print('MS: '+bot+' look set | '+_control_status(fake))
        )
    );
    null
);

control_look_up(target) -> _control_look_direction(target, 'up');
control_look_down(target) -> _control_look_direction(target, 'down');
control_look_north(target) -> _control_look_direction(target, 'north');
control_look_south(target) -> _control_look_direction(target, 'south');
control_look_east(target) -> _control_look_direction(target, 'east');
control_look_west(target) -> _control_look_direction(target, 'west');

_control_look_direction(target, direction) -> (
    fake = _online_fake_or_error(target);
    if(fake != null,
        (
            bot = fake~'name';
            if(direction == 'up', modify(fake, 'pitch', -90));
            if(direction == 'down', modify(fake, 'pitch', 90));
            if(direction == 'north', (modify(fake, 'yaw', 180); modify(fake, 'pitch', 0)));
            if(direction == 'south', (modify(fake, 'yaw', 0); modify(fake, 'pitch', 0)));
            if(direction == 'east', (modify(fake, 'yaw', -90); modify(fake, 'pitch', 0)));
            if(direction == 'west', (modify(fake, 'yaw', 90); modify(fake, 'pitch', 0)));
            print('MS: '+bot+' looking '+direction+' | '+_control_status(fake))
        )
    );
    null
);

control_turn_left(target) -> _control_turn(target, -90, 'left');
control_turn_right(target) -> _control_turn(target, 90, 'right');
control_turn_back(target) -> _control_turn(target, 180, 'back');

_control_turn(target, yaw_change, direction) -> (
    fake = _online_fake_or_error(target);
    if(fake != null,
        (
            bot = fake~'name';
            modify(fake, 'yaw', fake~'yaw' + yaw_change);
            print('MS: '+bot+' turned '+direction+' | '+_control_status(fake))
        )
    );
    null
);

control_stop(target) -> (
    fake = _online_fake_or_error(target);
    if(fake != null,
        (
            bot = fake~'name';
            delete(global_control_moves, str(bot));
            delete(global_fetchall, str(bot));
            delete(global_fetchall_returns, str(bot));
            run('player '+bot+' stop');
            print('MS: stopped all actions for '+bot)
        )
    );
    null
);

_full_dimension(dimension) -> if(
    str(dimension) == 'overworld', 'minecraft:overworld',
    str(dimension) == 'the_nether', 'minecraft:the_nether',
    'minecraft:the_end'
);

_find(id) -> global_presets:(str(id));

_put_box_ids() -> (
    boxes = read_file('ms_put_boxes', 'shared_nbt');
    if(boxes == null, [], keys(parse_nbt(boxes)))
);

_control_yaw_suggestions(args) -> (
    if(!has(args, 'control_target'),
        [0],
        (
            target = str(args:'control_target');
            fake = player(target);
            if(fake != null && fake~'player_type' == 'fake',
                [str('%.2f', fake~'yaw')],
                (
                    preset = _find(target);
                    if(preset != null,
                        [str('%.2f', preset:'yaw')],
                        [0]
                    )
                )
            )
        )
    )
);

add(id, bot, x, y, z, yaw, pitch, dimension) -> (
    if(_find(id) != null,
        print('MS: ID '+id+' already exists. Remove it first.'),
        (
            preset = nbt('{}');
            preset:'bot' = bot;
            preset:'x' = x;
            preset:'y' = y;
            preset:'z' = z;
            preset:'yaw' = yaw;
            preset:'pitch' = pitch;
            // Store the short name. A colon in an NBT assignment is parsed as a path.
            preset:'dimension' = dimension;
            global_presets:(str(id)) = preset;
            store_app_data(global_presets);
            print('MS: added ['+id+'] '+bot+' @ '+x+' '+y+' '+z+' facing '+yaw+' '+pitch+' in '+dimension)
        )
    );
    null
);

// run: click and leave quickly; keep: spawn and stay without clicking;
// walk: click, wait 20 ticks (about 1 second), then leave. A minecart starting
// from rest needs about 13 ticks to cross three powered rails.
run_preset(id) -> _start_preset(id, 4, true);

keep_preset(id) -> _start_preset(id, -1, false);

walk_preset(id) -> _start_preset(id, 20, true);

fight_preset(id, attack_interval) -> (
    preset = _find(id);
    if(preset == null,
        print('MS: ID '+id+' was not found.'),
        (
            bot = str(preset:'bot');
            online = player(bot);
            if(online != null,
                if(online~'player_type' == 'fake',
                    (
                        print('MS: using online fake player ['+id+'] '+bot);
                        schedule(2, '_fight_bot', id, bot, attack_interval)
                    ),
                    print('MS: '+bot+' is online but is not a fake player.')
                ),
                (
                    spawn_command = 'player '+bot+' spawn at '+preset:'x'+' '+preset:'y'+' '+preset:'z'+' facing '+preset:'yaw'+' '+preset:'pitch'+' in '+_full_dimension(preset:'dimension');
                    run(spawn_command);
                    schedule(1, '_wait_for_fighter', id, bot, 400, attack_interval);
                    print('MS: spawning fighter ['+id+'] '+bot)
                )
            )
        )
    );
    null
);

_wait_for_fighter(id, bot, ticks_left, attack_interval) -> (
    fake = player(bot);
    if(fake != null && fake~'player_type' == 'fake',
        (
            print('MS: fake player online ['+id+'] '+bot);
            schedule(2, '_fight_bot', id, bot, attack_interval)
        ),
        if(ticks_left > 0,
            schedule(1, '_wait_for_fighter', id, bot, ticks_left-1, attack_interval),
            print('MS: spawn timeout ['+id+'] '+bot)
        )
    )
);

_fight_bot(id, bot, attack_interval) -> (
    fake = player(bot);
    if(fake != null && fake~'player_type' == 'fake',
        (
            run('player '+bot+' attack interval '+attack_interval);
            print('MS: fighter active ['+id+'] '+bot+' | attack every '+attack_interval+' gt. Use /ms run '+id+' to exit.')
        ),
        print('MS: fake player is no longer online ['+id+'] '+bot)
    )
);

drink_preset(id) -> (
    preset = _find(id);
    if(preset == null,
        print('MS: ID '+id+' was not found.'),
        (
            bot = str(preset:'bot');
            online = player(bot);
            if(online != null,
                if(online~'player_type' == 'fake',
                    (
                        print('MS: using online fake player ['+id+'] '+bot);
                        schedule(2, '_drink_bot', id, bot)
                    ),
                    print('MS: '+bot+' is online but is not a fake player.')
                ),
                (
                    spawn_command = 'player '+bot+' spawn at '+preset:'x'+' '+preset:'y'+' '+preset:'z'+' facing '+preset:'yaw'+' '+preset:'pitch'+' in '+_full_dimension(preset:'dimension');
                    run(spawn_command);
                    schedule(1, '_wait_for_drinker', id, bot, 400);
                    print('MS: spawning drinker ['+id+'] '+bot)
                )
            )
        )
    );
    null
);

_wait_for_drinker(id, bot, ticks_left) -> (
    fake = player(bot);
    if(fake != null && fake~'player_type' == 'fake',
        (
            print('MS: fake player online ['+id+'] '+bot);
            schedule(2, '_drink_bot', id, bot)
        ),
        if(ticks_left > 0,
            schedule(1, '_wait_for_drinker', id, bot, ticks_left-1),
            print('MS: spawn timeout ['+id+'] '+bot)
        )
    )
);

_drink_bot(id, bot) -> (
    fake = player(bot);
    if(fake != null && fake~'player_type' == 'fake',
        (
            run('player '+bot+' use continuous');
            print('MS: continuous use active ['+id+'] '+bot+'. Use /ms run '+id+' to exit.')
        ),
        print('MS: fake player is no longer online ['+id+'] '+bot)
    )
);

create_here(bot) -> _create_bot(bot, player());

create_at_player(bot, target_name) -> _create_bot(bot, player(str(target_name)));

_create_bot(bot, target) -> (
    bot = str(bot);
    if(target == null,
        print('MS: target player is not online.'),
        if(player(bot) != null,
            print('MS: player '+bot+' is already online.'),
            (
                [tx, ty, tz, tyaw, tpitch] = target~'location';
                target_name = target~'name';
                target_dimension = target~'dimension';
                spawn_command = 'player '+bot+' spawn at '+tx+' '+ty+' '+tz+' facing '+tyaw+' '+tpitch+' in '+target_dimension;
                run(spawn_command);
                schedule(1, '_wait_for_created_bot', bot, str(target_name), 400);
                print('MS: creating '+bot+' at '+target_name+' position')
            )
        )
    );
    null
);

_wait_for_created_bot(bot, target_name, ticks_left) -> (
    fake = player(bot);
    if(fake != null && fake~'player_type' == 'fake',
        print('MS: fake player created '+bot+' at '+target_name+' position'),
        if(ticks_left > 0,
            schedule(1, '_wait_for_created_bot', bot, target_name, ticks_left-1),
            print('MS: create timeout '+bot)
        )
    )
);

send_here(bot) -> _send_bot(bot, player());

send_to_player(bot, target_name) -> _send_bot(bot, player(str(target_name)));

// Keep the complex inventory code in fetch.sc. If that optional app fails,
// the core ms app and all existing commands still load normally.
fetch_here(bot) -> (
    run('fetch '+str(bot));
    null
);

fetch_to_player(bot, target_name) -> (
    run('fetch '+str(bot)+' '+str(target_name));
    null
);

// Keep deposit and route code in put.sc. The core ms app only forwards
// commands, so an optional put failure cannot remove the rest of /ms.
put_here(bot) -> (
    run('put '+str(bot));
    null
);

put_saved(bot, id) -> (
    run('put '+str(bot)+' '+str(id));
    null
);

put_add(id) -> (
    run('put add '+str(id)+' '+str(player()~'name'));
    null
);

put_add_at_current_dimension(id, x, y, z, yaw, pitch) -> (
    current_dimension = str(player()~'dimension');
    short_dimension = if(
        current_dimension ~ 'the_nether$', 'the_nether',
        current_dimension ~ 'the_end$', 'the_end',
        'overworld'
    );
    put_add_at_dimension(id, x, y, z, yaw, pitch, short_dimension)
);

put_add_at_dimension(id, x, y, z, yaw, pitch, dimension) -> (
    run(
        'put addat '+str(id)+' '+x+' '+y+' '+z+' '+yaw+' '+pitch+' '+str(dimension)
    );
    null
);

put_list() -> (
    run('put list');
    null
);

put_remove(id) -> (
    run('put remove '+str(id));
    null
);

// The native companion mod performs the recipe simulation transactionally.
// With no IDs it uses only the fake inventory. Box mode takes ingredients from
// the first registered put box and delivers products to the second one.
craftbox_self(bot) -> (
    run('mscraftbox '+str(bot));
    null
);

craftbox_saved(bot, source_id, output_id) -> (
    run('mscraftbox '+str(bot)+' '+str(source_id)+' '+str(output_id));
    null
);

// Keep item dropping separate from the core command tree for the same reason
// as put/fetch: an optional app failure must not remove the rest of /ms.
throw_all(bot) -> (
    run('throw '+str(bot));
    null
);

// The mstrade Fabric companion mod performs native villager offers and keeps
// the more complex code outside this core Scarpet command tree.
trade_fake(bot) -> (
    run('mstrade '+str(bot));
    null
);

trade_chain_run(bot, chain_id) -> (
    run('mstrade '+str(bot)+' '+str(chain_id));
    null
);

trade_chain_add(chain_id) -> (
    run('mstrade chain add '+str(chain_id));
    null
);

trade_chain_remove(chain_id) -> (
    run('mstrade chain remove '+str(chain_id));
    null
);

trade_chain_capture(chain_id, radius) -> (
    run('mstrade chain capture '+str(chain_id)+' '+str(radius));
    null
);

trade_chain_list_all() -> (
    run('mstrade chain list');
    null
);

trade_chain_list_one(chain_id) -> (
    run('mstrade chain list '+str(chain_id));
    null
);

trade_chain_delete(chain_id) -> (
    run('mstrade chain delete '+str(chain_id));
    null
);

fetchall_current_here(target) -> _start_fetchall(target, 'l', 0, null, str(player()~'name'));
fetchall_current_to_player(target, target_name) -> _start_fetchall(target, 'l', 0, str(target_name), str(target_name));

fetchall_left_here(target, distance) -> _start_fetchall(target, 'l', distance, null, str(player()~'name'));
fetchall_left_to_player(target, distance, target_name) -> _start_fetchall(target, 'l', distance, str(target_name), str(target_name));

fetchall_right_here(target, distance) -> _start_fetchall(target, 'r', distance, null, str(player()~'name'));
fetchall_right_to_player(target, distance, target_name) -> _start_fetchall(target, 'r', distance, str(target_name), str(target_name));

_start_fetchall(target, side, distance, finish_target, blocked_target) -> (
    fake = _online_fake_or_error(target);
    if(fake != null,
        (
            bot = str(fake~'name');
            if(global_fetchall:bot != null,
                print('MS: fetchall is already active for '+bot),
                (
                    token = str(time())+'_'+rand(1000000);
                    global_fetchall:bot = token;
                    global_fetchall_returns:bot = {
                        'finish' -> finish_target,
                        'blocked' -> blocked_target
                    };
                    start_yaw = fake~'yaw';
                    print('MS: fetchall started for '+bot+' | side='+side+' distance='+distance+' | columns='+(distance+1));
                    schedule(1, '_fetchall_angle', bot, str(side), distance, 0, start_yaw, 0, token)
                )
            )
        )
    );
    null
);

_fetchall_angle(bot, side, distance, step, fixed_yaw, angle_index, token) -> (
    if(global_fetchall:bot != token,
        null,
        (
            fake = player(bot);
            if(fake == null || fake~'player_type' != 'fake',
                (
                    delete(global_fetchall, bot);
                    delete(global_fetchall_returns, bot);
                    print('MS: fetchall cancelled because '+bot+' is offline')
                ),
                (
                    angles = [60, 0, -45, -75];
                    pitch = angles:angle_index;
                    modify(fake, 'yaw', fixed_yaw);
                    modify(fake, 'pitch', pitch);
                    print('MS: fetchall column '+(step+1)+'/'+(distance+1)+' | pitch='+pitch);
                    run('fetch '+bot);
                    if(angle_index < 3,
                        schedule(1, '_fetchall_angle', bot, side, distance, step, fixed_yaw, angle_index+1, token),
                        if(step >= distance,
                            schedule(1, '_fetchall_finish', bot, side, distance, token),
                            schedule(1, '_fetchall_start_move', bot, side, distance, step, fixed_yaw, token)
                        )
                    )
                )
            )
        )
    )
);

_fetchall_start_move(bot, side, distance, step, fixed_yaw, token) -> (
    if(global_fetchall:bot == token,
        (
            fake = player(bot);
            if(fake == null || fake~'player_type' != 'fake',
                (
                    delete(global_fetchall, bot);
                    delete(global_fetchall_returns, bot);
                    print('MS: fetchall cancelled because '+bot+' is offline')
                ),
                (
                    modify(fake, 'yaw', fixed_yaw);
                    modify(fake, 'pitch', 0);
                    start_x = fake~'x';
                    start_z = fake~'z';
                    look_vector = fake~'look';
                    side_factor = if(side == 'l', 1, -1);
                    target_x = start_x + side_factor*look_vector:2;
                    target_z = start_z - side_factor*look_vector:0;
                    move_direction = if(side == 'l', 'left', 'right');
                    run('player '+bot+' move '+move_direction);
                    schedule(1, '_fetchall_move_tick', bot, side, distance, step, fixed_yaw, start_x, start_z, target_x, target_z, token, 100)
                )
            )
        )
    )
);

_fetchall_move_tick(bot, side, distance, step, fixed_yaw, start_x, start_z, target_x, target_z, token, ticks_left) -> (
    if(global_fetchall:bot != token,
        null,
        (
            fake = player(bot);
            if(fake == null || fake~'player_type' != 'fake',
                (
                    delete(global_fetchall, bot);
                    delete(global_fetchall_returns, bot);
                    print('MS: fetchall cancelled because '+bot+' is offline')
                ),
                (
                    dx = fake~'x' - start_x;
                    dz = fake~'z' - start_z;
                    travelled = sqrt(dx*dx + dz*dz);
                    if(travelled >= 1,
                        (
                            run('player '+bot+' move');
                            modify(fake, 'x', target_x);
                            modify(fake, 'z', target_z);
                            schedule(1, '_fetchall_angle', bot, side, distance, step+1, fixed_yaw, 0, token)
                        ),
                        if(ticks_left <= 0,
                            (
                                run('player '+bot+' move');
                                return_data = global_fetchall_returns:bot;
                                delete(global_fetchall, bot);
                                delete(global_fetchall_returns, bot);
                                print('MS: fetchall stopped: '+bot+' could not move to the next block | '+_control_status(fake));
                                if(return_data != null && return_data:'blocked' != null,
                                    (
                                        print('MS: sending '+bot+' to '+return_data:'blocked'+' after movement failure');
                                        _send_bot(bot, player(str(return_data:'blocked')))
                                    )
                                )
                            ),
                            schedule(1, '_fetchall_move_tick', bot, side, distance, step, fixed_yaw, start_x, start_z, target_x, target_z, token, ticks_left-1)
                        )
                    )
                )
            )
        )
    )
);

_fetchall_finish(bot, side, distance, token) -> (
    if(global_fetchall:bot == token,
        (
            fake = player(bot);
            return_data = global_fetchall_returns:bot;
            delete(global_fetchall, bot);
            delete(global_fetchall_returns, bot);
            if(fake != null && fake~'player_type' == 'fake',
                (
                    print('MS: fetchall complete for '+bot+' | side='+side+' distance='+distance+' columns='+(distance+1)+' | '+_control_status(fake));
                    if(return_data != null && return_data:'finish' != null,
                        (
                            print('MS: sending '+bot+' to '+return_data:'finish');
                            _send_bot(bot, player(str(return_data:'finish')))
                        )
                    )
                ),
                print('MS: fetchall complete; '+bot+' is offline')
            )
        )
    )
);

clear_fake_inventory(bot) -> (
    bot = str(bot);
    fake = player(bot);
    if(fake == null,
        print('MS: player '+bot+' is not online.'),
        if(fake~'player_type' != 'fake',
            print('MS: refused to clear '+bot+': target is not a fake player.'),
            (
                cleared_stacks = 0;
                cleared_items = 0;
                for(range(36),
                    (
                        stack = inventory_get(fake, _);
                        if(stack != null,
                            (
                                cleared_stacks += 1;
                                cleared_items += stack:1;
                                inventory_set(fake, _, 0)
                            )
                        )
                    )
                );
                print('MS: cleared '+cleared_stacks+' stack(s), '+cleared_items+' item(s) from fake player '+bot+'.')
            )
        )
    );
    null
);

_send_bot(bot, target) -> (
    bot = str(bot);
    fake = player(bot);
    if(fake == null,
        print('MS: player '+bot+' is not online.'),
        if(fake~'player_type' != 'fake',
            print('MS: '+bot+' is online but is not a fake player.'),
            if(target == null,
                print('MS: target player is not online.'),
                (
                    [tx, ty, tz, tyaw, tpitch] = target~'location';
                    target_name = str(target~'name');
                    target_dimension = str(target~'dimension');
                    run('player '+bot+' kill');
                    schedule(1, '_wait_for_send_exit', bot, target_name, tx, ty, tz, tyaw, tpitch, target_dimension, 40);
                    print('MS: moving '+bot+' to '+target_name+' position')
                )
            )
        )
    );
    null
);

_wait_for_send_exit(bot, target_name, tx, ty, tz, tyaw, tpitch, target_dimension, ticks_left) -> (
    if(player(bot) == null,
        (
            spawn_command = 'player '+bot+' spawn at '+tx+' '+ty+' '+tz+' facing '+tyaw+' '+tpitch+' in '+target_dimension;
            run(spawn_command);
            schedule(1, '_wait_for_sent_bot', bot, target_name, 400)
        ),
        if(ticks_left > 0,
            (
                run('player '+bot+' kill');
                schedule(1, '_wait_for_send_exit', bot, target_name, tx, ty, tz, tyaw, tpitch, target_dimension, ticks_left-1)
            ),
            print('MS: failed to disconnect '+bot)
        )
    )
);

_wait_for_sent_bot(bot, target_name, ticks_left) -> (
    fake = player(bot);
    if(fake != null && fake~'player_type' == 'fake',
        print('MS: moved '+bot+' to '+target_name+' position'),
        if(ticks_left > 0,
            schedule(1, '_wait_for_sent_bot', bot, target_name, ticks_left-1),
            print('MS: reconnect timeout '+bot)
        )
    )
);

_start_preset(id, leave_delay, should_use) -> (
    preset = _find(id);
    if(preset == null,
        print('MS: ID '+id+' was not found.'),
        (
            bot = str(preset:'bot');
            online = player(bot);
            if(online != null,
                if(online~'player_type' == 'fake',
                    (
                        print('MS: using online fake player ['+id+'] '+bot);
                        if(should_use,
                            schedule(2, '_use_bot', id, bot, leave_delay),
                            schedule(2, '_keep_bot_without_use', id, bot)
                        )
                    ),
                    print('MS: '+bot+' is online but is not a fake player.')
                ),
                (
                    spawn_command = 'player '+bot+' spawn at '+preset:'x'+' '+preset:'y'+' '+preset:'z'+' facing '+preset:'yaw'+' '+preset:'pitch'+' in '+_full_dimension(preset:'dimension');
                    // /player may return no command result even when it succeeds.
                    run(spawn_command);
                    schedule(1, '_wait_for_bot', id, bot, 400, leave_delay, should_use);
                    print('MS: spawning ['+id+'] '+bot)
                )
            )
        )
    );
    null
);

_wait_for_bot(id, bot, ticks_left, leave_delay, should_use) -> (
    fake = player(bot);
    if(fake != null && fake~'player_type' == 'fake',
        (
            print('MS: fake player online ['+id+'] '+bot);
            if(should_use,
                schedule(2, '_use_bot', id, bot, leave_delay),
                schedule(2, '_keep_bot_without_use', id, bot)
            )
        ),
        if(ticks_left > 0,
            schedule(1, '_wait_for_bot', id, bot, ticks_left-1, leave_delay, should_use),
            print('MS: spawn timeout ['+id+'] '+bot)
        )
    )
);

_keep_bot_without_use(id, bot) -> (
    fake = player(bot);
    if(fake != null && fake~'player_type' == 'fake',
        print('MS: fake player kept online without interaction ['+id+'] '+bot),
        print('MS: fake player is no longer online ['+id+'] '+bot)
    )
);

_use_bot(id, bot, leave_delay) -> (
    global_pending_clicks:(bot) = id;
    run('player '+bot+' use once');
    print('MS: right-click sent ['+id+'] '+bot);
    if(leave_delay < 0,
        schedule(4, '_finish_keep', id, bot),
        (
            if(leave_delay > 4,
                print('MS: keeping fake player online for '+leave_delay+' ticks ['+id+'] '+bot)
            );
            schedule(leave_delay, '_leave_bot', id, bot)
        )
    )
);

__on_player_interacts_with_block(fake, hand, block, face, hitvec) -> (
    bot = fake~'name';
    id = global_pending_clicks:(bot);
    if(id != null,
        (
            print('MS: block interaction confirmed ['+id+'] '+bot+' @ '+pos(block));
            delete(global_pending_clicks:(bot))
        )
    )
);

_finish_keep(id, bot) -> (
    if(global_pending_clicks:(bot) != null,
        (
            print('MS: no block interaction confirmed ['+id+'] '+bot);
            delete(global_pending_clicks:(bot))
        )
    );
    fake = player(bot);
    if(fake != null && fake~'player_type' == 'fake',
        print('MS: fake player kept online ['+id+'] '+bot+'. Use /ms run '+id+' to click and exit.'),
        print('MS: fake player is no longer online ['+id+'] '+bot)
    )
);

_leave_bot(id, bot) -> (
    if(global_pending_clicks:(bot) != null,
        (
            print('MS: no block interaction confirmed ['+id+'] '+bot);
            delete(global_pending_clicks:(bot))
        )
    );
    run('player '+bot+' kill');
    schedule(2, '_check_left', id, bot, 20)
);

_check_left(id, bot, tries_left) -> (
    if(player(bot) == null,
        print('MS: fake player exited ['+id+'] '+bot),
        if(tries_left > 0,
            (
                run('player '+bot+' kill');
                schedule(2, '_check_left', id, bot, tries_left-1)
            ),
            print('MS: failed to exit ['+id+'] '+bot)
        )
    )
);

list_bots() -> (
    fake_players = filter(player('all'), _~'player_type' == 'fake');
    if(length(fake_players) == 0,
        print('MS: no fake players are online.'),
        (
            print('MS: online fake players ('+length(fake_players)+'):');
            for(fake_players,
                (
                    [bx, by, bz, byaw, bpitch] = _~'location';
                    held = _~['holds', 'mainhand'];
                    held_text = if(held == null, 'empty', held:0+' x'+held:1);
                    print(
                        _~'name'+' | '+
                        str('%.2f', bx)+' '+str('%.2f', by)+' '+str('%.2f', bz)+' | '+
                        'yaw '+str('%.1f', byaw)+' pitch '+str('%.1f', bpitch)+' | '+
                        str(_~'dimension')+' | mainhand '+held_text
                    )
                )
            )
        )
    );
    null
);

show_preset(id) -> (
    preset = _find(id);
    if(preset == null,
        print('MS: ID '+id+' was not found.'),
        (
            bot = str(preset:'bot');
            online = player(bot);
            status = if(
                online == null, 'offline',
                online~'player_type' == 'fake', 'online (fake)',
                'online (not fake)'
            );
            print('MS: preset ['+id+']');
            print('  bot: '+bot);
            print('  position: '+preset:'x'+' '+preset:'y'+' '+preset:'z');
            print('  facing: yaw '+preset:'yaw'+' pitch '+preset:'pitch');
            print('  dimension: '+preset:'dimension');
            print('  status: '+status)
        )
    );
    null
);

list_presets() -> (
    ids = keys(parse_nbt(global_presets));
    if(length(ids) == 0,
        print('MS: no saved commands.'),
        (
            print('MS: saved commands ('+length(ids)+'):');
            for(sort(ids),
                preset = global_presets:_;
                print('['+_+'] '+preset:'bot'+' | '+preset:'x'+' '+preset:'y'+' '+preset:'z'+' | yaw '+preset:'yaw'+' pitch '+preset:'pitch'+' | '+preset:'dimension')
            )
        )
    );
    null
);

remove_preset(id) -> (
    preset = _find(id);
    if(preset == null,
        print('MS: ID '+id+' was not found.'),
        (
            delete(global_presets:(str(id)));
            store_app_data(global_presets);
            print('MS: removed ['+id+'] '+preset:'bot')
        )
    );
    null
);
